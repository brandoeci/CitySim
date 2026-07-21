package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.model.SpeedOverride;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.car.CarSpawner;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.event.EventService;
import edu.escuelaing.citysim.engine.room.RoomManager;
import edu.escuelaing.citysim.engine.room.RoomSimulation;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import edu.escuelaing.citysim.engine.traffic.TrafficLightController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private static final int FORCE_GREEN_SECONDS = 30;
    private static final long GREEN_WAVE_STAGGER_MS = 2000;
    private static final int SPEED_TRAP_SECONDS = 60;
    private static final double SPEED_TRAP_FACTOR = 0.5;
    private static final int SPEED_BOOST_SECONDS = 45;
    private static final double SPEED_BOOST_FACTOR = 2.0;
    private static final int TRAFFIC_BOMB_CAR_COUNT = 25;
    private static final long TRAFFIC_BOMB_COOLDOWN_MS = 60_000;
    private static final int DISTRICT_SHIELD_SECONDS = 20;
    private static final long DISTRICT_SHIELD_COOLDOWN_MS = 90_000;

    private final SpaceDataGrid space;
    private final CityMap cityMap;
    private final DistrictService districtService;
    private final JwtService jwtService;
    private final EventService eventService;
    private final RoomManager roomManager;
    private final TrafficLightController trafficLightController;
    private final SimulationProperties simulationProperties;
    private final SimulationClock simulationClock;
    private final CarSpawner carSpawner;

    /** Cooldown de LLUVIA DE TRAFICO en modo global. En modo sala se usa el mapa propio de cada RoomSimulation. */
    private final Map<String, Long> globalTrafficBombCooldowns = new ConcurrentHashMap<>();

    /** Cooldown de ESCUDO DE DISTRITO en modo global. En modo sala se usa el mapa propio de cada RoomSimulation. */
    private final Map<String, Long> globalDistrictShieldCooldowns = new ConcurrentHashMap<>();

    public ToolController(SpaceDataGrid space, CityMap cityMap,
                          DistrictService districtService, JwtService jwtService,
                          EventService eventService, RoomManager roomManager,
                          TrafficLightController trafficLightController,
                          SimulationProperties simulationProperties,
                          SimulationClock simulationClock,
                          CarSpawner carSpawner) {
        this.space = space;
        this.cityMap = cityMap;
        this.districtService = districtService;
        this.jwtService = jwtService;
        this.eventService = eventService;
        this.roomManager = roomManager;
        this.trafficLightController = trafficLightController;
        this.simulationProperties = simulationProperties;
        this.simulationClock = simulationClock;
        this.carSpawner = carSpawner;
    }

    @PostMapping("/close-edge")
    public ResponseEntity<Map<String, Object>> closeEdge(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        EventService myEvents = room != null ? room.getEventService() : eventService;

        String edgeId = body.get("edgeId");
        if (edgeId == null) return error("Falta el campo 'edgeId'");

        Edge edge = cityMap.getEdge(edgeId);
        if (edge == null) return error("La via no existe: " + edgeId);

        District mine = myDistricts.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        if (!mine.zoneIds().contains(edge.zoneId()))
            return error("Esa via no esta en tu distrito");

        mySpace.blockEdge(edgeId, username);
        String reverseId = reverseOf(edgeId);
        if (cityMap.getEdge(reverseId) != null) {
            mySpace.blockEdge(reverseId, username);
        }

        // Si esta era la via objetivo del evento activo, el aporte se registra
        // solo: la accion en el mapa ES la respuesta al evento. No hay boton.
        boolean contributed = false;
        String headZone = mine.headZone();
        if (headZone != null) {
            contributed = myEvents.tryRegisterByEdge(headZone, edgeId);
        }

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("closed", true);
        ok.put("edgeId", edgeId);
        ok.put("by", username);
        ok.put("eventContribution", contributed);
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/open-edge")
    public ResponseEntity<Map<String, Object>> openEdge(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;

        String edgeId = body.get("edgeId");
        if (edgeId == null) return error("Falta el campo 'edgeId'");

        String owner = mySpace.getBlockedEdgesWithOwner().get(edgeId);
        if (owner == null) return error("Esa via no esta cerrada");

        if (!owner.equals(username))
            return error("Esa via la cerro " + owner);

        mySpace.unblockEdge(edgeId);
        mySpace.unblockEdge(reverseOf(edgeId));

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("opened", true);
        ok.put("edgeId", edgeId);
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/force-green")
    public ResponseEntity<Map<String, Object>> forceGreen(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        TrafficLightController myLights = room != null ? room.getTrafficLightController() : trafficLightController;

        Object idRaw = body.get("intersectionId");
        String intersectionId = idRaw != null ? idRaw.toString() : null;
        if (intersectionId == null) return error("Falta el campo 'intersectionId'");

        boolean horizontal = Boolean.TRUE.equals(body.get("horizontal"));

        Node node = cityMap.getNode(intersectionId);
        if (node == null) return error("El cruce no existe: " + intersectionId);

        if (!myLights.isMajorIntersection(intersectionId))
            return error("Ese cruce no tiene semaforo (no es un cruce mayor)");

        District mine = myDistricts.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        if (!mine.zoneIds().contains(node.zoneId()))
            return error("Ese cruce no esta en tu distrito");

        int durationTicks = (int) (FORCE_GREEN_SECONDS * 1000L / simulationProperties.getTickRateMs());
        myLights.forceGreen(intersectionId, horizontal, durationTicks);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("forced", true);
        ok.put("intersectionId", intersectionId);
        ok.put("horizontal", horizontal);
        ok.put("durationSeconds", FORCE_GREEN_SECONDS);
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/green-wave")
    public ResponseEntity<Map<String, Object>> greenWave(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        TrafficLightController myLights = room != null ? room.getTrafficLightController() : trafficLightController;

        Object idRaw = body.get("intersectionId");
        String intersectionId = idRaw != null ? idRaw.toString() : null;
        if (intersectionId == null) return error("Falta el campo 'intersectionId'");

        boolean horizontal = Boolean.TRUE.equals(body.get("horizontal"));

        Node node = cityMap.getNode(intersectionId);
        if (node == null) return error("El cruce no existe: " + intersectionId);

        if (!myLights.isMajorIntersection(intersectionId))
            return error("Ese cruce no tiene semaforo (no es un cruce mayor)");

        District mine = myDistricts.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        if (!mine.zoneIds().contains(node.zoneId()))
            return error("Ese cruce no esta en tu distrito");

        List<String> avenue = myLights.intersectionsAlongAvenue(intersectionId, horizontal).stream()
                .filter(id -> {
                    Node n = cityMap.getNode(id);
                    return n != null && mine.zoneIds().contains(n.zoneId());
                })
                .toList();

        int durationTicks = (int) (FORCE_GREEN_SECONDS * 1000L / simulationProperties.getTickRateMs());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "green-wave-" + intersectionId);
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < avenue.size(); i++) {
            String id = avenue.get(i);
            scheduler.schedule(() -> myLights.forceGreen(id, horizontal, durationTicks),
                    i * GREEN_WAVE_STAGGER_MS, TimeUnit.MILLISECONDS);
        }
        scheduler.shutdown();

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("wave", true);
        ok.put("intersectionId", intersectionId);
        ok.put("horizontal", horizontal);
        ok.put("intersectionCount", avenue.size());
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/traffic-bomb")
    public ResponseEntity<Map<String, Object>> trafficBomb(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        CarSpawner mySpawner = room != null ? room.getCarSpawner() : carSpawner;
        Map<String, Long> cooldowns = room != null ? room.getTrafficBombCooldowns() : globalTrafficBombCooldowns;

        Object txRaw = body.get("targetX");
        Object tyRaw = body.get("targetY");
        if (!(txRaw instanceof Number) || !(tyRaw instanceof Number))
            return error("Faltan los campos 'targetX'/'targetY'");

        District mine = myDistricts.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        String targetNodeId = nearestNodeId(((Number) txRaw).doubleValue(), ((Number) tyRaw).doubleValue());
        Node targetNode = cityMap.getNode(targetNodeId);
        if (targetNode == null) return error("Ese punto esta fuera del mapa");

        if (mine.zoneIds().contains(targetNode.zoneId()))
            return error("Apunta fuera de tu propio distrito");

        long now = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(username);
        if (lastUsed != null && now - lastUsed < TRAFFIC_BOMB_COOLDOWN_MS) {
            long remaining = (TRAFFIC_BOMB_COOLDOWN_MS - (now - lastUsed) + 999) / 1000;
            return error("Espera " + remaining + "s para volver a usar la lluvia de trafico");
        }
        cooldowns.put(username, now);

        int spawned = mySpawner.spawnTargeted(TRAFFIC_BOMB_CAR_COUNT, targetNodeId);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("triggered", true);
        ok.put("targetNodeId", targetNodeId);
        ok.put("carsSpawned", spawned);
        ok.put("cooldownSeconds", TRAFFIC_BOMB_COOLDOWN_MS / 1000);
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/district-shield")
    public ResponseEntity<Map<String, Object>> districtShield(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        Map<String, Long> cooldowns = room != null ? room.getDistrictShieldCooldowns() : globalDistrictShieldCooldowns;

        District mine = myDistricts.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        long now = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(username);
        if (lastUsed != null && now - lastUsed < DISTRICT_SHIELD_COOLDOWN_MS) {
            long remaining = (DISTRICT_SHIELD_COOLDOWN_MS - (now - lastUsed) + 999) / 1000;
            return error("Espera " + remaining + "s para volver a usar el escudo");
        }
        cooldowns.put(username, now);

        // Vias frontera: un extremo dentro del distrito, el otro fuera. Como
        // cityMap.getEdges() ya trae ambas direcciones como Edge separados, no
        // hace falta buscar el reverso a mano (a diferencia de close-edge).
        List<String> borderEdges = new ArrayList<>();
        for (Edge edge : cityMap.getEdges().values()) {
            Node src = cityMap.getNode(edge.sourceNodeId());
            Node tgt = cityMap.getNode(edge.targetNodeId());
            if (src == null || tgt == null) continue;
            boolean srcIn = mine.zoneIds().contains(src.zoneId());
            boolean tgtIn = mine.zoneIds().contains(tgt.zoneId());
            if (srcIn != tgtIn) borderEdges.add(edge.id());
        }

        for (String edgeId : borderEdges) {
            mySpace.blockEdge(edgeId, username);
        }

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(DISTRICT_SHIELD_SECONDS * 1000L);
                for (String edgeId : borderEdges) {
                    mySpace.unblockEdge(edgeId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("shielded", true);
        ok.put("edgeCount", borderEdges.size());
        ok.put("durationSeconds", DISTRICT_SHIELD_SECONDS);
        ok.put("cooldownSeconds", DISTRICT_SHIELD_COOLDOWN_MS / 1000);
        return ResponseEntity.ok(ok);
    }

    /** Nodo mas cercano a un punto de mundo (BLOCK_SIZE=10, igual convenio que MapFactory). */
    private String nearestNodeId(double x, double y) {
        int row = (int) Math.round(y / 10.0);
        int col = (int) Math.round(x / 10.0);
        row = Math.max(0, Math.min(row, cityMap.getGridHeight() - 1));
        col = Math.max(0, Math.min(col, cityMap.getGridWidth() - 1));
        return "N_" + row + "_" + col;
    }

    @PostMapping("/speed-trap")
    public ResponseEntity<Map<String, Object>> speedTrap(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        return applySpeedOverride(authHeader, body, SPEED_TRAP_FACTOR, SPEED_TRAP_SECONDS);
    }

    @PostMapping("/speed-boost")
    public ResponseEntity<Map<String, Object>> speedBoost(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        return applySpeedOverride(authHeader, body, SPEED_BOOST_FACTOR, SPEED_BOOST_SECONDS);
    }

    /** Logica comun de REDUCTOR y TURBO: misma validacion de distrito que close-edge. */
    private ResponseEntity<Map<String, Object>> applySpeedOverride(
            String authHeader, Map<String, String> body, double factor, int durationSeconds) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        long currentTick = room != null ? room.getClock().getTickNumber() : simulationClock.getTickNumber();

        String edgeId = body.get("edgeId");
        if (edgeId == null) return error("Falta el campo 'edgeId'");

        Edge edge = cityMap.getEdge(edgeId);
        if (edge == null) return error("La via no existe: " + edgeId);

        District mine = myDistricts.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        if (!mine.zoneIds().contains(edge.zoneId()))
            return error("Esa via no esta en tu distrito");

        long durationTicks = durationSeconds * 1000L / simulationProperties.getTickRateMs();
        long expiresAtTick = currentTick + durationTicks;

        mySpace.putSpeedOverride(new SpeedOverride(edgeId, factor, expiresAtTick, username));
        String reverseId = reverseOf(edgeId);
        if (cityMap.getEdge(reverseId) != null) {
            mySpace.putSpeedOverride(new SpeedOverride(reverseId, factor, expiresAtTick, username));
        }

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("applied", true);
        ok.put("edgeId", edgeId);
        ok.put("factor", factor);
        ok.put("durationSeconds", durationSeconds);
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/blocked-edges")
    public ResponseEntity<Map<String, Object>> blockedEdges(
            @RequestHeader("Authorization") String authHeader) {
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        Map<String, String> blocked = mySpace.getBlockedEdgesWithOwner();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("blocked", blocked);
        body.put("count", blocked.size());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/speed-overrides")
    public ResponseEntity<Map<String, Object>> speedOverrides(
            @RequestHeader("Authorization") String authHeader) {
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        Map<String, SpeedOverride> overrides = mySpace.getSpeedOverrides();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("overrides", overrides);
        body.put("count", overrides.size());
        return ResponseEntity.ok(body);
    }

    private String reverseOf(String edgeId) {
        if (edgeId.endsWith("_R")) return edgeId.substring(0, edgeId.length() - 2);
        return edgeId + "_R";
    }

    private ResponseEntity<Map<String, Object>> error(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", msg);
        return ResponseEntity.badRequest().body(body);
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        return jwtService.extractUsername(token);
    }

    /**
     * Si el token trae roomCode, RESUCITA esa sala si no estaba corriendo
     * (idempotente); si no trae roomCode, modo global de siempre. Antes usaba
     * getRoom() de solo lectura: si el backend se reiniciaba mientras un
     * usuario tenia una sala abierta, su JWT seguia siendo valido pero la
     * sala nunca volvia a levantarse, y esto caia en silencio al modo global.
     */
    private RoomSimulation resolveRoom(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        String roomCode = jwtService.extractRoomCode(token);
        return roomCode != null ? roomManager.startRoom(roomCode) : null;
    }
}