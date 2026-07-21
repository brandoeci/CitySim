package edu.escuelaing.citysim.engine.event;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import edu.escuelaing.citysim.core.model.EventObjective;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.model.ObjectiveKind;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Agrega las observaciones locales de cada {@code ZoneProcessingUnit} (mapa
 * {@code event-observations}, un escritor por zona) en los contadores vivos
 * de cada {@link EventObjective}, y decide si el evento se resuelve, falla o
 * sigue activo.
 *
 * Igual que {@code EventService.generateEvent()}, esto corre gated detras de
 * {@link EventGeneratorLeader#isLeader()}: un solo nodo del cluster evalua y
 * reescribe el EventState compartido, asi que no hay carrera de escritores
 * concurrentes sobre el agregado (no hace falta EntryProcessor: el patron ya
 * usado en este codebase para mutaciones programadas es "un solo escritor
 * gated por lider", y este servicio lo sigue igual).
 */
@Service
public class EventObjectiveTracker {

    private final IMap<String, ObjectiveZoneSnapshot> observationsMap;
    private final SpaceDataGrid space;
    private final EventBroadcaster broadcaster;
    private final EventRepository eventRepository;
    private final EventGeneratorLeader leader;

    @Autowired
    public EventObjectiveTracker(HazelcastInstance hazelcast, SpaceDataGrid space,
                                 EventBroadcaster broadcaster, EventRepository eventRepository,
                                 EventGeneratorLeader leader) {
        this(hazelcast, space, broadcaster, eventRepository, leader, "");
    }

    /** @param prefix antepuesto al mapa crudo "event-observations", vacio para el global. */
    public EventObjectiveTracker(HazelcastInstance hazelcast, SpaceDataGrid space,
                                 EventBroadcaster broadcaster, EventRepository eventRepository,
                                 EventGeneratorLeader leader, String prefix) {
        this.observationsMap = hazelcast.getMap(prefix + "event-observations");
        this.space = space;
        this.broadcaster = broadcaster;
        this.eventRepository = eventRepository;
        this.leader = leader;
    }

    /** Llamado por EventService al arrancar un evento nuevo: descarta datos del anterior. */
    public void resetObservations() {
        observationsMap.clear();
    }

    /** Llamado por la ZoneProcessingUnit duena de una zona, una vez por tick a lo sumo. */
    public void reportZone(String zoneId, ObjectiveZoneSnapshot snapshot) {
        observationsMap.set(zoneId, snapshot);
    }

    @Scheduled(fixedDelay = 1000)
    public void evaluate() {
        if (!leader.isLeader()) return;

        EventState current = space.getActiveEvent();
        if (current == null || !"ACTIVE".equals(current.status())) return;

        ObjectiveKind kind = EventType.valueOf(current.type()).getObjectiveKind();
        if (kind == ObjectiveKind.CLOSE_EDGE) return;   // se resuelve al vuelo, no aqui

        Collection<ObjectiveZoneSnapshot> snapshots = observationsMap.values();
        if (snapshots.isEmpty()) return;

        Map<String, EventObjective> updated = new HashMap<>(current.objectives());
        boolean changed = false;
        boolean anyFailed = false;

        for (Map.Entry<String, EventObjective> entry : current.objectives().entrySet()) {
            EventObjective objective = entry.getValue();
            if (objective.completed() || objective.failed()) {
                if (objective.failed()) anyFailed = true;
                continue;
            }

            EventObjective recomputed = recompute(objective, snapshots);
            if (!recomputed.equals(objective)) {
                updated.put(entry.getKey(), recomputed);
                changed = true;
            }
            if (recomputed.failed()) anyFailed = true;
        }

        if (!changed) return;

        EventState next = new EventState(current.eventId(), current.type(), current.status(),
                current.affectedZoneId(), current.description(), current.durationSeconds(),
                current.requiredActions(), current.startedAt(), current.resolvedAt(),
                Collections.unmodifiableMap(updated));

        if (anyFailed) {
            next = next.withStatus("FAILED", Instant.now());
            space.clearActiveEvent();
            persistFinalStatus(next);
        } else if (!kind.isSurvival() && next.isResolved()) {
            next = next.withStatus("RESOLVED", Instant.now());
            space.clearActiveEvent();
            persistFinalStatus(next);
        } else {
            space.putActiveEvent(next);
        }

        broadcaster.broadcast(next);
    }

    private static EventObjective recompute(EventObjective objective, Collection<ObjectiveZoneSnapshot> snapshots) {
        return switch (objective.kind()) {
            case SHIELD_AREA -> recomputeShield(objective, snapshots);
            case CLEAR_CORRIDOR -> recomputeCorridor(objective, snapshots);
            case EVACUATE_AREA -> recomputeEvacuation(objective, snapshots);
            case RELIEVE_JUNCTION -> recomputeJunction(objective, snapshots);
            case CLOSE_EDGE -> objective;
        };
    }

    // Estaticos y sin dependencias de instancia a proposito: no tocan
    // observationsMap/space/etc, asi que EventObjectiveTrackerLogicTest
    // (mismo paquete) puede probar la matematica de victoria/derrota de cada
    // mecanica llamandolos directamente, sin Hazelcast ni Spring.
    static EventObjective recomputeShield(EventObjective objective, Collection<ObjectiveZoneSnapshot> snapshots) {
        int totalEntries = 0;
        for (ObjectiveZoneSnapshot s : snapshots) {
            Integer v = s.areaEntryCumulative().get(objective.zoneId());
            if (v != null) totalEntries += v;
        }
        int remaining = Math.max(0, objective.threshold() - totalEntries);
        EventObjective next = objective.withCurrent(remaining);
        return remaining <= 0 ? next.withFailed() : next;
    }

    static EventObjective recomputeCorridor(EventObjective objective, Collection<ObjectiveZoneSnapshot> snapshots) {
        boolean dirty = false;
        for (ObjectiveZoneSnapshot s : snapshots) {
            if (Boolean.TRUE.equals(s.corridorDirty().get(objective.zoneId()))) {
                dirty = true;
                break;
            }
        }
        int next = dirty ? 0 : objective.current() + 1;
        EventObjective updated = objective.withCurrent(next);
        return next >= objective.threshold() ? updated.withCompleted() : updated;
    }

    static EventObjective recomputeEvacuation(EventObjective objective, Collection<ObjectiveZoneSnapshot> snapshots) {
        int inside = 0;
        for (ObjectiveZoneSnapshot s : snapshots) {
            Integer v = s.areaInside().get(objective.zoneId());
            if (v != null) inside += v;
        }
        EventObjective updated = objective.withCurrent(inside);
        return inside < objective.threshold() ? updated.withCompleted() : updated;
    }

    static EventObjective recomputeJunction(EventObjective objective, Collection<ObjectiveZoneSnapshot> snapshots) {
        int waiting = 0;
        for (ObjectiveZoneSnapshot s : snapshots) {
            Integer v = s.junctionWaiting().get(objective.zoneId());
            if (v != null) waiting += v;
        }
        EventObjective updated = objective.withCurrent(waiting);
        return waiting > objective.threshold() ? updated.withFailed() : updated;
    }

    private void persistFinalStatus(EventState state) {
        eventRepository.findById(state.eventId()).ifPresent(entity -> {
            entity.setStatus(EventStatus.valueOf(state.status()));
            entity.setResolvedAt(state.resolvedAt());
            state.objectives().forEach((zoneId, objective) -> {
                if (objective.completed()) entity.getActionsByZone().put(zoneId, 1);
            });
            eventRepository.save(entity);
        });
    }
}
