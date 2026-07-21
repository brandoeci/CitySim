package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.EventObjective;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.model.ObjectiveKind;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.District;
import edu.escuelaing.citysim.engine.zone.DistrictService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class EventService {

    private static final int SHIELD_TOLERANCE = 25;
    private static final int EVACUATION_THRESHOLD = 5;
    private static final int CORRIDOR_SECONDS = 15;
    private static final int CORRIDOR_LENGTH = 6;
    private static final int JUNCTION_THRESHOLD = 12;
    private static final double AREA_HALF_SIZE = 15.0;

    private final EventRepository eventRepository;
    private final EventBroadcaster broadcaster;
    private final SpaceDataGrid space;
    private final EventGeneratorLeader leader;
    private final DistrictService districtService;
    private final CityMap cityMap;
    private final EventObjectiveTracker tracker;

    private static final Random RANDOM = new Random();

    private static final Map<EventType, String> DESCRIPTIONS = Map.of(
            EventType.TRAFFIC_JAM,  "Congestion masiva: cada distrito debe cerrar la via saturada",
            EventType.ACCIDENT,     "Accidente multiple: cada distrito debe cerrar la via afectada",
            EventType.ROAD_CLOSURE, "Mantenimiento de emergencia: cada distrito debe cerrar su tramo",
            EventType.EMERGENCY,    "Emergencia critica: cada distrito debe cerrar su via prioritaria",
            EventType.VIP_CONVOY,   "Convoy presidencial: el corredor debe quedar despejado",
            EventType.AREA_SHIELD,  "Zona escolar en horario de salida: protege el area sin dejar que el trafico la atraviese",
            EventType.EVACUATION,   "Fuga de gas: evacua el sector antes de que se acabe el tiempo",
            EventType.GRIDLOCK,     "Trancon critico en el cruce: bajalo antes de que colapse"
    );

    public EventService(EventRepository eventRepository, EventBroadcaster broadcaster,
                        SpaceDataGrid space, EventGeneratorLeader leader,
                        DistrictService districtService, CityMap cityMap,
                        EventObjectiveTracker tracker) {
        this.eventRepository = eventRepository;
        this.broadcaster = broadcaster;
        this.space = space;
        this.leader = leader;
        this.districtService = districtService;
        this.cityMap = cityMap;
        this.tracker = tracker;
    }

    /**
     * Se revisa cada 2s, no cada 120s: en cuanto un evento se resuelve/expira/
     * falla, space.clearActiveEvent() lo borra de inmediato, asi que el
     * siguiente chequeo (a lo sumo 2s despues) ya ve el slot libre y dispara
     * el proximo evento -- sin la pausa de "calma" que tenia el intervalo
     * largo.
     */
    @Scheduled(fixedDelay = 2000)
    public void generateEvent() {
        if (!leader.isLeader()) return;
        if (space.getActiveEvent() != null) return;

        // El evento afecta a TODA la ciudad: cada distrito activo recibe su
        // propio objetivo. Sin administradores conectados no hay evento.
        List<District> districts = districtService.getDistricts();
        if (districts.isEmpty()) return;

        EventType type = EventType.values()[RANDOM.nextInt(EventType.values().length)];
        ObjectiveKind kind = type.getObjectiveKind();

        Map<String, Integer> carCountByZone = (kind == ObjectiveKind.EVACUATE_AREA)
                ? countCarsByZone() : Collections.emptyMap();

        Map<String, EventObjective> objectives = new HashMap<>();
        for (District d : districts) {
            EventObjective objective = buildObjective(d, kind, carCountByZone);
            if (objective != null) {
                objectives.put(d.headZone(), objective);
            }
        }
        if (objectives.isEmpty()) return;

        SimulationEvent entity = new SimulationEvent();
        entity.setType(type);
        entity.setStatus(EventStatus.ACTIVE);
        entity.setAffectedZoneId("ALL");
        entity.setDescription(DESCRIPTIONS.get(type));
        entity.setDurationSeconds(90);
        entity.setRequiredActions(objectives.size());   // todos los distritos deben actuar
        entity.setStartedAt(Instant.now());
        SimulationEvent saved = eventRepository.save(entity);

        EventState eventState = new EventState(
                saved.getId(), type.name(), "ACTIVE", "ALL",
                saved.getDescription(), saved.getDurationSeconds(),
                objectives.size(), saved.getStartedAt(), null,
                Collections.unmodifiableMap(objectives)
        );

        tracker.resetObservations();
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

    // Sin modificador de acceso a proposito: EventObjectiveGenerationTest
    // (mismo paquete) construye objetivos directamente contra un CityMap real
    // para verificar que los ids generados (vias, corredores, cruces) existen
    // de verdad, sin tener que esperar al sorteo aleatorio de generateEvent().
    EventObjective buildObjective(District d, ObjectiveKind kind, Map<String, Integer> carCountByZone) {
        String headZone = d.headZone();
        if (headZone == null) return null;

        return switch (kind) {
            case CLOSE_EDGE -> buildCloseEdgeObjective(d, headZone);
            case SHIELD_AREA -> buildShieldObjective(d, headZone);
            case EVACUATE_AREA -> buildEvacuationObjective(d, headZone, carCountByZone);
            case CLEAR_CORRIDOR -> buildCorridorObjective(d, headZone);
            case RELIEVE_JUNCTION -> buildJunctionObjective(d, headZone);
        };
    }

    private EventObjective buildCloseEdgeObjective(District d, String headZone) {
        String edgeId = pickEdgeIn(d);
        if (edgeId == null) return null;
        return new EventObjective(headZone, ObjectiveKind.CLOSE_EDGE, List.of(edgeId),
                null, null, null, null, null, 1, 0, false, false);
    }

    private EventObjective buildShieldObjective(District d, String headZone) {
        String zoneId = pickRandomZoneIn(d);
        double[] rect = areaRectAround(zoneId);
        if (rect == null) return null;
        return new EventObjective(headZone, ObjectiveKind.SHIELD_AREA, List.of(),
                rect[0], rect[1], rect[2], rect[3], null,
                SHIELD_TOLERANCE, SHIELD_TOLERANCE, false, false);
    }

    private EventObjective buildEvacuationObjective(District d, String headZone, Map<String, Integer> carCountByZone) {
        String zoneId = pickDensestZoneIn(d, carCountByZone);
        double[] rect = areaRectAround(zoneId);
        if (rect == null) return null;
        // Aproximacion inicial: cuenta de la zona SBA completa, no del rectangulo
        // 30x30 exacto. El tracker corrige el valor con su primera evaluacion.
        int carsInside = carCountByZone.getOrDefault(zoneId, 0);
        return new EventObjective(headZone, ObjectiveKind.EVACUATE_AREA, List.of(),
                rect[0], rect[1], rect[2], rect[3], null,
                EVACUATION_THRESHOLD, carsInside, false, false);
    }

    private EventObjective buildCorridorObjective(District d, String headZone) {
        List<String> chain = pickCorridorIn(d);
        if (chain == null) return null;
        return new EventObjective(headZone, ObjectiveKind.CLEAR_CORRIDOR, chain,
                null, null, null, null, null, CORRIDOR_SECONDS, 0, false, false);
    }

    private EventObjective buildJunctionObjective(District d, String headZone) {
        String nodeId = pickJunctionIn(d);
        if (nodeId == null) return null;
        return new EventObjective(headZone, ObjectiveKind.RELIEVE_JUNCTION, List.of(),
                null, null, null, null, nodeId, JUNCTION_THRESHOLD, 0, false, false);
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

    private String pickRandomZoneIn(District d) {
        if (d.zoneIds().isEmpty()) return null;
        return d.zoneIds().get(RANDOM.nextInt(d.zoneIds().size()));
    }

    private String pickDensestZoneIn(District d, Map<String, Integer> carCountByZone) {
        String best = null;
        int bestCount = -1;
        for (String zoneId : d.zoneIds()) {
            int count = carCountByZone.getOrDefault(zoneId, 0);
            if (count > bestCount) {
                bestCount = count;
                best = zoneId;
            }
        }
        return best;
    }

    /** Rectangulo de 30x30 unidades de mundo centrado en una zona, recortado al mapa. */
    private double[] areaRectAround(String zoneId) {
        if (zoneId == null) return null;
        Zone zone = cityMap.getZone(zoneId);
        if (zone == null) return null;

        double cx = (zone.minX() + zone.maxX()) / 2;
        double cy = (zone.minY() + zone.maxY()) / 2;

        double minX = Math.max(0, cx - AREA_HALF_SIZE);
        double minY = Math.max(0, cy - AREA_HALF_SIZE);
        double maxX = Math.min(cityMap.getWorldWidth(), cx + AREA_HALF_SIZE);
        double maxY = Math.min(cityMap.getWorldHeight(), cy + AREA_HALF_SIZE);
        return new double[]{minX, minY, maxX, maxY};
    }

    /** Cadena de 6 tramos consecutivos sobre una avenida, dentro del distrito. */
    private List<String> pickCorridorIn(District d) {
        List<String> avenueEdges = new ArrayList<>();
        for (String zoneId : d.zoneIds()) {
            Zone zone = cityMap.getZone(zoneId);
            if (zone == null) continue;
            for (String edgeId : zone.edgeIds()) {
                if (edgeId.endsWith("_R")) continue;
                if (isAvenueEdge(edgeId)) avenueEdges.add(edgeId);
            }
        }
        Collections.shuffle(avenueEdges, RANDOM);
        for (String start : avenueEdges) {
            List<String> chain = buildChain(start, CORRIDOR_LENGTH);
            if (chain != null) return chain;
        }
        return null;
    }

    private boolean isAvenueEdge(String edgeId) {
        String[] parts = edgeId.split("_");
        boolean horizontal = "H".equals(parts[1]);
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);
        return horizontal ? (row % 10 == 0) : (col % 10 == 0);
    }

    private List<String> buildChain(String startEdgeId, int length) {
        String[] parts = startEdgeId.split("_");
        boolean horizontal = "H".equals(parts[1]);
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);

        List<String> chain = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            String eid = horizontal ? ("E_H_" + row + "_" + (col + i)) : ("E_V_" + (row + i) + "_" + col);
            if (cityMap.getEdge(eid) == null) return null;
            chain.add(eid);
        }
        return chain;
    }

    /** Nodo cruce (fila y columna multiplo de 10) dentro del distrito. */
    private String pickJunctionIn(District d) {
        List<String> candidates = new ArrayList<>();
        for (String zoneId : d.zoneIds()) {
            Zone zone = cityMap.getZone(zoneId);
            if (zone == null) continue;
            for (String nodeId : zone.nodeIds()) {
                if (isMajorJunction(nodeId)) candidates.add(nodeId);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private boolean isMajorJunction(String nodeId) {
        String[] parts = nodeId.split("_");
        int row = Integer.parseInt(parts[1]);
        int col = Integer.parseInt(parts[2]);
        return row % 10 == 0 && col % 10 == 0;
    }

    private Map<String, Integer> countCarsByZone() {
        Map<String, Integer> counts = new HashMap<>();
        for (CarState c : space.getAllCars().values()) {
            counts.merge(c.getCurrentZoneId(), 1, Integer::sum);
        }
        return counts;
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

        EventObjective objective = current.objectiveFor(zoneId);
        if (objective == null)
            throw new IllegalArgumentException("Tu distrito no tiene objetivo en este evento");

        applyResponse(current, zoneId, objective.withCompleted());
    }

    /**
     * Llamado desde la herramienta de cerrar via: si el administrador cerro
     * justamente la via objetivo de su distrito, su aporte se registra solo.
     * No hay boton "aportar": la accion en el mapa ES la respuesta.
     */
    public boolean tryRegisterByEdge(String districtHeadZone, String edgeId) {
        if (districtHeadZone == null) return false;
        EventState current = space.getActiveEvent();
        if (current == null || !"ACTIVE".equals(current.status())) return false;

        EventObjective objective = current.objectiveFor(districtHeadZone);
        if (objective == null || objective.kind() != ObjectiveKind.CLOSE_EDGE) return false;
        if (objective.completed()) return false;
        if (!edgeId.equals(objective.targetEdge())) return false;

        applyResponse(current, districtHeadZone, objective.withCurrent(1).withCompleted());
        return true;
    }

    private void applyResponse(EventState current, String zoneId, EventObjective updatedObjective) {
        EventState updated = current.withObjective(zoneId, updatedObjective);

        ObjectiveKind kind = EventType.valueOf(updated.type()).getObjectiveKind();
        if (!kind.isSurvival() && updated.isResolved()) {
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

        // Eventos de supervivencia (AREA_SHIELD, GRIDLOCK) que siguen ACTIVE al
        // agotarse el tiempo significa que ningun distrito fallo: se resuelven.
        // Los demas, si no todos cumplieron, expiran.
        ObjectiveKind kind = EventType.valueOf(current.type()).getObjectiveKind();
        String finalStatus = kind.isSurvival() ? "RESOLVED" : "EXPIRED";

        EventState finished = current.withStatus(finalStatus, Instant.now());
        space.clearActiveEvent();
        updateEntity(finished);
        broadcaster.broadcast(finished);
    }

    private void updateEntity(EventState state) {
        eventRepository.findById(state.eventId()).ifPresent(entity -> {
            entity.setStatus(EventStatus.valueOf(state.status()));
            entity.setResolvedAt(state.resolvedAt());
            state.objectives().forEach((zoneId, objective) -> {
                if (objective.completed()) entity.getActionsByZone().put(zoneId, 1);
            });
            eventRepository.save(entity);
        });
    }

    public EventState getActiveEvent() { return space.getActiveEvent(); }

    public List<SimulationEvent> getHistory() {
        return eventRepository.findAllByOrderByStartedAtDesc();
    }
}
