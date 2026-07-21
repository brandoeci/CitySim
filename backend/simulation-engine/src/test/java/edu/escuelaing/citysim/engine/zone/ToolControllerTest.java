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
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import edu.escuelaing.citysim.engine.traffic.TrafficLightController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ToolController resuelve siempre en "modo global" en estas pruebas (el token
 * de prueba no trae roomCode, asi que resolveRoom() devuelve null y el
 * controller usa los campos globales inyectados) -- eso evita tener que
 * construir o mockear una RoomSimulation real solo para probar la logica de
 * validacion, que es identica en ambos modos.
 */
@ExtendWith(MockitoExtension.class)
class ToolControllerTest {

    private static final String TOKEN = "valid-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final String USERNAME = "ana";

    @Mock private SpaceDataGrid space;
    @Mock private DistrictService districtService;
    @Mock private JwtService jwtService;
    @Mock private EventService eventService;
    @Mock private RoomManager roomManager;
    @Mock private TrafficLightController trafficLightController;
    @Mock private SimulationClock simulationClock;
    @Mock private CarSpawner carSpawner;

    private ToolController controller;
    private District myDistrict;

    @BeforeEach
    void setUp() {
        CityMap cityMap = buildCityMap();
        SimulationProperties props = new SimulationProperties();
        props.setTickRateMs(50);

        controller = new ToolController(space, cityMap, districtService, jwtService, eventService,
                roomManager, trafficLightController, props, simulationClock, carSpawner);

        myDistrict = new District(0, USERNAME, List.of("Z_0_0"), 0, 0);

        lenient().when(jwtService.isValid(TOKEN)).thenReturn(true);
        lenient().when(jwtService.extractUsername(TOKEN)).thenReturn(USERNAME);
        lenient().when(jwtService.extractRoomCode(TOKEN)).thenReturn(null);
    }

    /**
     * NA/NB en la zona del distrito (Z_0_0); NC y N_5_5 fuera (Z_OUT).
     * N_0_0 tambien dentro, para el caso "apuntaste a tu propio distrito".
     * E1 conecta NA-NB (dentro del distrito); E_BORDER conecta NA-NC (cruza
     * la frontera del distrito, para la prueba de ESCUDO).
     */
    private static CityMap buildCityMap() {
        Map<String, Node> nodes = new LinkedHashMap<>();
        nodes.put("NA", new Node("NA", 0, 0, "Z_0_0", true, List.of(), List.of()));
        nodes.put("NB", new Node("NB", 10, 0, "Z_0_0", true, List.of(), List.of()));
        nodes.put("NC", new Node("NC", 20, 0, "Z_OUT", true, List.of(), List.of()));
        nodes.put("N_0_0", new Node("N_0_0", 0, 0, "Z_0_0", true, List.of(), List.of()));
        nodes.put("N_5_5", new Node("N_5_5", 50, 50, "Z_OUT", true, List.of(), List.of()));

        Map<String, Edge> edges = new LinkedHashMap<>();
        edges.put("E1", new Edge("E1", "NA", "NB", 10, 10, 1, false, "Z_0_0"));
        edges.put("E_OUT_ZONE", new Edge("E_OUT_ZONE", "NA", "NB", 10, 10, 1, false, "Z_OUT"));
        edges.put("E_BORDER", new Edge("E_BORDER", "NA", "NC", 10, 10, 1, false, "Z_0_0"));

        Map<String, edu.escuelaing.citysim.core.map.Zone> zones = new LinkedHashMap<>();
        return new CityMap(nodes, edges, zones, 100, 100);
    }

    // ---- closeEdge ----

    @Test
    void closeEdgeRechazaTokenInvalido() {
        when(jwtService.isValid("bad")).thenReturn(false);

        ResponseEntity<Map<String, Object>> resp =
                controller.closeEdge("Bearer bad", Map.of("edgeId", "E1"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Token invalido", resp.getBody().get("error"));
    }

    @Test
    void closeEdgeCierraLaViaDentroDeMiDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);

        ResponseEntity<Map<String, Object>> resp =
                controller.closeEdge(AUTH_HEADER, Map.of("edgeId", "E1"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(true, resp.getBody().get("closed"));
        verify(space).blockEdge("E1", USERNAME);
        // E1_R no existe en el CityMap de prueba: no debe intentar bloquear un id inexistente.
        verify(space, never()).blockEdge(eq("E1_R"), any());
    }

    @Test
    void closeEdgeRechazaViaFueraDeMiDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);

        ResponseEntity<Map<String, Object>> resp =
                controller.closeEdge(AUTH_HEADER, Map.of("edgeId", "E_OUT_ZONE"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Esa via no esta en tu distrito", resp.getBody().get("error"));
        verify(space, never()).blockEdge(anyString(), anyString());
    }

    @Test
    void closeEdgeRechazaUsuarioSinDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp =
                controller.closeEdge(AUTH_HEADER, Map.of("edgeId", "E1"));

        assertEquals("No administras ningun distrito", resp.getBody().get("error"));
    }

    @Test
    void closeEdgeRechazaViaInexistente() {
        // No hace falta stubear el distrito: closeEdge revisa que la via exista
        // antes de mirar el distrito, asi que nunca llega a consultarlo.
        ResponseEntity<Map<String, Object>> resp =
                controller.closeEdge(AUTH_HEADER, Map.of("edgeId", "NO_EXISTE"));

        assertEquals("La via no existe: NO_EXISTE", resp.getBody().get("error"));
    }

    // ---- forceGreen ----

    @Test
    void forceGreenFuerzaElSemaforoDeUnCruceMayorEnMiDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);
        when(trafficLightController.isMajorIntersection("NA")).thenReturn(true);

        ResponseEntity<Map<String, Object>> resp = controller.forceGreen(
                AUTH_HEADER, Map.of("intersectionId", "NA", "horizontal", true));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(true, resp.getBody().get("forced"));
        // 30s de FORCE_GREEN_SECONDS a 50ms/tick = 600 ticks.
        verify(trafficLightController).forceGreen("NA", true, 600);
    }

    @Test
    void forceGreenRechazaCruceSinSemaforo() {
        // No hace falta stubear el distrito: forceGreen revisa si el cruce
        // tiene semaforo antes de mirar el distrito, asi que nunca lo consulta.
        when(trafficLightController.isMajorIntersection("NA")).thenReturn(false);

        ResponseEntity<Map<String, Object>> resp = controller.forceGreen(
                AUTH_HEADER, Map.of("intersectionId", "NA", "horizontal", true));

        assertEquals("Ese cruce no tiene semaforo (no es un cruce mayor)", resp.getBody().get("error"));
        verify(trafficLightController, never()).forceGreen(anyString(), anyBoolean(), anyInt());
    }

    @Test
    void forceGreenRechazaCruceFueraDeMiDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);
        when(trafficLightController.isMajorIntersection("NC")).thenReturn(true);

        ResponseEntity<Map<String, Object>> resp = controller.forceGreen(
                AUTH_HEADER, Map.of("intersectionId", "NC", "horizontal", true));

        assertEquals("Ese cruce no esta en tu distrito", resp.getBody().get("error"));
    }

    // ---- speed-trap / speed-boost ----

    @Test
    void speedTrapAplicaElFactorReducidoConLaDuracionCorrecta() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);
        when(simulationClock.getTickNumber()).thenReturn(1000L);

        ResponseEntity<Map<String, Object>> resp =
                controller.speedTrap(AUTH_HEADER, Map.of("edgeId", "E1"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        // 60s a 50ms/tick = 1200 ticks; expira en 1000 + 1200 = 2200.
        verify(space).putSpeedOverride(new SpeedOverride("E1", 0.5, 2200L, USERNAME));
    }

    @Test
    void speedBoostAplicaElFactorAceleradoConLaDuracionCorrecta() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);
        when(simulationClock.getTickNumber()).thenReturn(0L);

        ResponseEntity<Map<String, Object>> resp =
                controller.speedBoost(AUTH_HEADER, Map.of("edgeId", "E1"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        // 45s a 50ms/tick = 900 ticks.
        verify(space).putSpeedOverride(new SpeedOverride("E1", 2.0, 900L, USERNAME));
    }

    // ---- traffic-bomb ----

    @Test
    void trafficBombDisparaCarrosHaciaFueraDeMiDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);
        when(carSpawner.spawnTargeted(25, "N_5_5")).thenReturn(25);

        ResponseEntity<Map<String, Object>> resp = controller.trafficBomb(
                AUTH_HEADER, Map.of("targetX", 50.0, "targetY", 50.0));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("N_5_5", resp.getBody().get("targetNodeId"));
        assertEquals(25, resp.getBody().get("carsSpawned"));
    }

    @Test
    void trafficBombRechazaApuntarDentroDeMiPropioDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);

        ResponseEntity<Map<String, Object>> resp = controller.trafficBomb(
                AUTH_HEADER, Map.of("targetX", 0.0, "targetY", 0.0));

        assertEquals("Apunta fuera de tu propio distrito", resp.getBody().get("error"));
        verify(carSpawner, never()).spawnTargeted(anyInt(), anyString());
    }

    @Test
    void trafficBombRespetaElCooldownEntreUsosDelMismoUsuario() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);
        when(carSpawner.spawnTargeted(anyInt(), anyString())).thenReturn(25);

        controller.trafficBomb(AUTH_HEADER, Map.of("targetX", 50.0, "targetY", 50.0));
        ResponseEntity<Map<String, Object>> second = controller.trafficBomb(
                AUTH_HEADER, Map.of("targetX", 50.0, "targetY", 50.0));

        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode());
        assertTrue(((String) second.getBody().get("error")).contains("Espera"));
        verify(carSpawner, times(1)).spawnTargeted(anyInt(), anyString());
    }

    // ---- district-shield ----

    @Test
    void districtShieldBloqueaSoloLasViasFronterizasDeMiDistrito() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);

        ResponseEntity<Map<String, Object>> resp = controller.districtShield(AUTH_HEADER);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().get("edgeCount"));
        verify(space).blockEdge("E_BORDER", USERNAME);
        verify(space, never()).blockEdge(eq("E1"), any());
    }

    @Test
    void districtShieldRespetaElCooldownEntreUsosDelMismoUsuario() {
        when(districtService.getDistrictOf(USERNAME)).thenReturn(myDistrict);

        controller.districtShield(AUTH_HEADER);
        ResponseEntity<Map<String, Object>> second = controller.districtShield(AUTH_HEADER);

        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode());
        assertTrue(((String) second.getBody().get("error")).contains("Espera"));
        verify(space, times(1)).blockEdge(anyString(), anyString());
    }

    // ---- blocked-edges / speed-overrides (lectura) ----

    @Test
    void blockedEdgesDevuelveElMapaDeViasBloqueadasDelEspacioGlobal() {
        when(space.getBlockedEdgesWithOwner()).thenReturn(Map.of("E1", USERNAME));

        ResponseEntity<Map<String, Object>> resp = controller.blockedEdges(AUTH_HEADER);

        assertEquals(1, resp.getBody().get("count"));
        assertEquals(Map.of("E1", USERNAME), resp.getBody().get("blocked"));
    }

    @Test
    void speedOverridesDevuelveElMapaDeMultiplicadoresActivos() {
        SpeedOverride override = new SpeedOverride("E1", 0.5, 500L, USERNAME);
        when(space.getSpeedOverrides()).thenReturn(Map.of("E1", override));

        ResponseEntity<Map<String, Object>> resp = controller.speedOverrides(AUTH_HEADER);

        assertEquals(1, resp.getBody().get("count"));
    }
}
