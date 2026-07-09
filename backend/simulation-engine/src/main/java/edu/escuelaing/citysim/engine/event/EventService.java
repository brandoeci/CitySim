package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventBroadcaster broadcaster;
    private final ZoneRegistry zoneRegistry;
    private final SpaceDataGrid space;
    private final EventGeneratorLeader leader;

    private static final Random RANDOM = new Random();

    private static final Map<EventType, String> DESCRIPTIONS = Map.of(
            EventType.TRAFFIC_JAM,  "Congestion masiva detectada en la zona",
            EventType.ACCIDENT,     "Accidente reportado, se requiere despeje inmediato",
            EventType.ROAD_CLOSURE, "Via cerrada por mantenimiento de emergencia",
            EventType.VIP_CONVOY,   "Convoy VIP en transito, despejar ruta",
            EventType.EMERGENCY,    "Emergencia critica, todas las zonas en alerta"
    );

    public EventService(EventRepository eventRepository, EventBroadcaster broadcaster,
                        ZoneRegistry zoneRegistry, SpaceDataGrid space,
                        EventGeneratorLeader leader) {
        this.eventRepository = eventRepository;
        this.broadcaster = broadcaster;
        this.zoneRegistry = zoneRegistry;
        this.space = space;
        this.leader = leader;
    }

    @Scheduled(fixedDelay = 120000)
    public void generateEvent() {
        if (!leader.isLeader()) return;
        if (space.getActiveEvent() != null) return;

        List<String> zones = new ArrayList<>(zoneRegistry.getOwnedZones().keySet());
        if (zones.isEmpty()) return;

        EventType type = EventType.values()[RANDOM.nextInt(EventType.values().length)];
        String zone = zones.get(RANDOM.nextInt(zones.size()));

        SimulationEvent entity = new SimulationEvent();
        entity.setType(type);
        entity.setStatus(EventStatus.ACTIVE);
        entity.setAffectedZoneId(zone);
        entity.setDescription(DESCRIPTIONS.get(type));
        entity.setDurationSeconds(60);
        entity.setRequiredActions(5);
        entity.setStartedAt(Instant.now());
        SimulationEvent saved = eventRepository.save(entity);

        EventState eventState = toState(saved);
        space.putActiveEvent(eventState);
        broadcaster.broadcast(eventState);

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(saved.getDurationSeconds() * 1000L);
                expireEvent(saved.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void registerAction(Long eventId, String zoneId) {
        EventState current = space.getActiveEvent();
        if (current == null || !current.eventId().equals(eventId)) return;
        if (!"ACTIVE".equals(current.status())) return;

        EventState updated = current.withAction(zoneId);

        if (updated.isResolved()) {
            updated = updated.withStatus("RESOLVED", Instant.now());
            space.clearActiveEvent();
            updateEntity(updated);
        } else {
            space.putActiveEvent(updated);
        }

        broadcaster.broadcast(updated);
    }

    private void expireEvent(Long eventId) {
        EventState current = space.getActiveEvent();
        if (current == null || !current.eventId().equals(eventId)) return;
        if (!"ACTIVE".equals(current.status())) return;

        EventState expired = current.withStatus("EXPIRED", Instant.now());
        space.clearActiveEvent();
        updateEntity(expired);
        broadcaster.broadcast(expired);
    }

    private void updateEntity(EventState state) {
        eventRepository.findById(state.eventId()).ifPresent(entity -> {
            entity.setStatus(EventStatus.valueOf(state.status()));
            entity.setResolvedAt(state.resolvedAt());
            state.actionsByZone().forEach((z, count) ->
                    entity.getActionsByZone().put(z, count));
            eventRepository.save(entity);
        });
    }

    private EventState toState(SimulationEvent entity) {
        return new EventState(
                entity.getId(),
                entity.getType().name(),
                entity.getStatus().name(),
                entity.getAffectedZoneId(),
                entity.getDescription(),
                entity.getDurationSeconds(),
                entity.getRequiredActions(),
                entity.getStartedAt(),
                entity.getResolvedAt(),
                Collections.unmodifiableMap(new HashMap<>(entity.getActionsByZone()))
        );
    }

    public EventState getActiveEvent() { return space.getActiveEvent(); }

    public List<SimulationEvent> getHistory() {
        return eventRepository.findAllByOrderByStartedAtDesc();
    }
}
