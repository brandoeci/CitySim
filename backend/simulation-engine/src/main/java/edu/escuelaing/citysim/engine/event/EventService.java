package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.District;
import edu.escuelaing.citysim.engine.zone.DistrictService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventBroadcaster broadcaster;
    private final SpaceDataGrid space;
    private final EventGeneratorLeader leader;
    private final DistrictService districtService;
    private final CityMap cityMap;

    private static final Random RANDOM = new Random();

    private static final Map<EventType, String> DESCRIPTIONS = Map.of(
            EventType.TRAFFIC_JAM,  "Congestion masiva: cada distrito debe cerrar la via saturada",
            EventType.ACCIDENT,     "Accidente multiple: cada distrito debe cerrar la via afectada",
            EventType.ROAD_CLOSURE, "Mantenimiento de emergencia: cada distrito debe cerrar su tramo",
            EventType.VIP_CONVOY,   "Convoy VIP: cada distrito debe despejar su tramo de la ruta",
            EventType.EMERGENCY,    "Emergencia critica: cada distrito debe cerrar su via prioritaria"
    );

    public EventService(EventRepository eventRepository, EventBroadcaster broadcaster,
                        SpaceDataGrid space, EventGeneratorLeader leader,
                        DistrictService districtService, CityMap cityMap) {
        this.eventRepository = eventRepository;
        this.broadcaster = broadcaster;
        this.space = space;
        this.leader = leader;
        this.districtService = districtService;
        this.cityMap = cityMap;
    }

    @Scheduled(fixedDelay = 120000)
    public void generateEvent() {
        if (!leader.isLeader()) return;
        if (space.getActiveEvent() != null) return;

        // El evento afecta a TODA la ciudad: cada distrito activo recibe su
        // propio objetivo. Sin administradores conectados no hay evento.
        List<District> districts = districtService.getDistricts();
        if (districts.isEmpty()) return;

        EventType type = EventType.values()[RANDOM.nextInt(EventType.values().length)];

        // Una via objetivo por distrito, dentro de su propio territorio.
        Map<String, String> targets = new HashMap<>();
        for (District d : districts) {
            String edgeId = pickEdgeIn(d);
            if (edgeId != null) {
                // La clave es la zona "cabecera" del distrito: identifica al distrito.
                targets.put(headZone(d), edgeId);
            }
        }
        if (targets.isEmpty()) return;

        SimulationEvent entity = new SimulationEvent();
        entity.setType(type);
        entity.setStatus(EventStatus.ACTIVE);
        entity.setAffectedZoneId("ALL");
        entity.setDescription(DESCRIPTIONS.get(type));
        entity.setDurationSeconds(90);
        entity.setRequiredActions(targets.size());   // todos los distritos deben actuar
        entity.setStartedAt(Instant.now());
        SimulationEvent saved = eventRepository.save(entity);

        EventState eventState = new EventState(
                saved.getId(), type.name(), "ACTIVE", "ALL",
                saved.getDescription(), saved.getDurationSeconds(),
                targets.size(), saved.getStartedAt(), null,
                Collections.emptyMap(),
                Collections.unmodifiableMap(targets),
                Collections.emptySet()
        );

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

    /** La zona que identifica al distrito (la primera de su franja). */
    private String headZone(District d) {
        return d.zoneIds().isEmpty() ? null : d.zoneIds().get(0);
    }

    /** Elige una via al azar dentro del territorio de un distrito. */
    private String pickEdgeIn(District d) {
        List<String> candidates = new ArrayList<>();
        for (String zoneId : d.zoneIds()) {
            Zone zone = cityMap.getZone(zoneId);
            if (zone == null) continue;
            for (String edgeId : zone.edgeIds()) {
                // Solo el sentido directo: al cerrar, el backend cierra ambos.
                if (!edgeId.endsWith("_R")) candidates.add(edgeId);
            }
            if (candidates.size() > 400) break;   // suficiente para elegir
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    /**
     * Registra que un distrito cumplio su objetivo.
     *
     * Ya no cuenta clicks: cuenta DISTRITOS distintos. Un mismo administrador
     * no puede resolver el evento solo apretando cinco veces. El evento se
     * resuelve unicamente cuando todos los distritos activos actuaron.
     */
    public void registerAction(Long eventId, String zoneId, EventAction action) {
        EventState current = space.getActiveEvent();
        if (current == null || !current.eventId().equals(eventId))
            throw new IllegalArgumentException("No hay un evento activo con id " + eventId);
        if (!"ACTIVE".equals(current.status()))
            throw new IllegalArgumentException("El evento ya no esta activo");

        EventType type = EventType.valueOf(current.type());
        EventAction required = type.getRequiredAction();
        if (action != required)
            throw new IllegalArgumentException(
                    "La accion " + action + " no resuelve un evento " + type +
                            ". Se requiere " + required);

        if (current.hasResponded(zoneId))
            throw new IllegalArgumentException("Tu distrito ya cumplio su objetivo");

        applyResponse(current, zoneId);
    }

    /**
     * Llamado desde la herramienta de cerrar via: si el administrador cerro
     * justamente la via objetivo de su distrito, su aporte se registra solo.
     * No hay boton "aportar": la accion en el mapa ES la respuesta.
     */
    public boolean tryRegisterByEdge(String districtHeadZone, String edgeId) {
        EventState current = space.getActiveEvent();
        if (current == null || !"ACTIVE".equals(current.status())) return false;
        if (current.hasResponded(districtHeadZone)) return false;

        String target = current.targetEdgeFor(districtHeadZone);
        if (target == null || !target.equals(edgeId)) return false;

        applyResponse(current, districtHeadZone);
        return true;
    }

    private void applyResponse(EventState current, String zoneId) {
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

    public EventState getActiveEvent() { return space.getActiveEvent(); }

    public List<SimulationEvent> getHistory() {
        return eventRepository.findAllByOrderByStartedAtDesc();
    }
}