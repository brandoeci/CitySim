package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.MapFactory;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.model.EventObjective;
import edu.escuelaing.citysim.core.model.ObjectiveKind;
import edu.escuelaing.citysim.engine.zone.District;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que los objetivos generados para las 4 mecanicas nuevas apuntan a
 * ids reales (vias, corredores, cruces) dentro de un CityMap real de tamano
 * de produccion (200x200, 10x10 zonas), sin pasar por Spring/Hazelcast ni por
 * el sorteo aleatorio de tipo en generateEvent() -- llama a buildObjective()
 * directamente, muchas veces, para cubrir la aleatoriedad interna de cada
 * mecanica de eleccion (zona/avenida/cruce).
 */
class EventObjectiveGenerationTest {

    private static final int RUNS = 60;

    private static CityMap cityMap;
    private static District districtA;
    private static District districtB;
    private static EventService service;

    @BeforeAll
    static void setUp() {
        cityMap = MapFactory.generate(200, 200, 10, 10);
        // Reproduce el reparto de DistrictService.partition() para 2 usuarios:
        // franjas de columnas [0-4] y [5-9].
        districtA = districtFor(0, 0, 4);
        districtB = districtFor(1, 5, 9);
        service = new EventService(null, null, null, null, null, cityMap, null);
    }

    private static District districtFor(int index, int minCol, int maxCol) {
        List<String> zoneIds = cityMap.getZones().values().stream()
                .filter(z -> z.zoneCol() >= minCol && z.zoneCol() <= maxCol)
                .sorted(Comparator.comparingInt(Zone::zoneRow).thenComparingInt(Zone::zoneCol))
                .map(Zone::id)
                .collect(Collectors.toList());
        return new District(index, "user" + index, zoneIds, minCol, maxCol);
    }

    // --- CLOSE_EDGE ----------------------------------------------------

    @Test
    void closeEdgeApuntaAUnaViaRealDelDistrito() {
        for (District d : List.of(districtA, districtB)) {
            for (int i = 0; i < RUNS; i++) {
                EventObjective obj = service.buildObjective(d, ObjectiveKind.CLOSE_EDGE, Map.of());
                assertNotNull(obj);
                assertEquals(1, obj.edgeIds().size());
                String edgeId = obj.edgeIds().get(0);
                Edge edge = cityMap.getEdge(edgeId);
                assertNotNull(edge, "edge " + edgeId + " deberia existir");
                assertTrue(d.zoneIds().contains(edge.zoneId()),
                        "edge " + edgeId + " deberia estar dentro del distrito " + d.index());
                assertFalse(edgeId.endsWith("_R"), "solo se elige el sentido directo");
            }
        }
    }

    // --- AREA_SHIELD -----------------------------------------------------

    @Test
    void shieldGeneraUnRectanguloValidoDentroDelMapa() {
        for (District d : List.of(districtA, districtB)) {
            for (int i = 0; i < RUNS; i++) {
                EventObjective obj = service.buildObjective(d, ObjectiveKind.SHIELD_AREA, Map.of());
                assertNotNull(obj);
                assertValidRect(obj);
                assertEquals(25, obj.threshold());
                assertEquals(25, obj.current());
            }
        }
    }

    // --- EVACUATION --------------------------------------------------------

    @Test
    void evacuacionPrefiereLaZonaConMasCarrosDelDistrito() {
        // La zona mas densa de districtA (cols 0-4) es una zona concreta;
        // el objetivo deberia centrarse en ella, no en una al azar.
        String denseZone = districtA.zoneIds().get(3);
        Map<String, Integer> carCounts = Map.of(denseZone, 40);

        EventObjective obj = service.buildObjective(districtA, ObjectiveKind.EVACUATE_AREA, carCounts);

        assertNotNull(obj);
        assertValidRect(obj);
        assertEquals(5, obj.threshold());
        assertEquals(40, obj.current());

        Zone zone = cityMap.getZone(denseZone);
        double cx = (zone.minX() + zone.maxX()) / 2;
        double cy = (zone.minY() + zone.maxY()) / 2;
        assertTrue(cx >= obj.minX() && cx <= obj.maxX());
        assertTrue(cy >= obj.minY() && cy <= obj.maxY());
    }

    @Test
    void evacuacionGeneraRectanguloValidoSinDatosDeCarros() {
        for (int i = 0; i < RUNS; i++) {
            EventObjective obj = service.buildObjective(districtB, ObjectiveKind.EVACUATE_AREA, Map.of());
            assertNotNull(obj);
            assertValidRect(obj);
            assertEquals(0, obj.current());
        }
    }

    // --- VIP_CONVOY (CLEAR_CORRIDOR) ----------------------------------------

    @Test
    void corredorEsUnaCadenaDeSeisTramosConsecutivosSobreUnaAvenida() {
        for (District d : List.of(districtA, districtB)) {
            for (int i = 0; i < RUNS; i++) {
                EventObjective obj = service.buildObjective(d, ObjectiveKind.CLEAR_CORRIDOR, Map.of());
                assertNotNull(obj);
                List<String> chain = obj.edgeIds();
                assertEquals(6, chain.size());
                assertEquals(15, obj.threshold());
                assertEquals(0, obj.current());

                boolean horizontal = chain.get(0).startsWith("E_H_");
                int[] prevRowCol = parseRowCol(chain.get(0));

                for (int idx = 0; idx < chain.size(); idx++) {
                    String edgeId = chain.get(idx);
                    assertNotNull(cityMap.getEdge(edgeId), "edge " + edgeId + " deberia existir");
                    assertFalse(edgeId.endsWith("_R"));

                    int[] rc = parseRowCol(edgeId);
                    if (horizontal) {
                        assertEquals(prevRowCol[0], rc[0], "misma fila en toda la cadena horizontal");
                        assertEquals(0, rc[0] % 10, "la avenida horizontal debe caer en fila multiplo de 10");
                        assertEquals(prevRowCol[1] + idx, rc[1], "columnas consecutivas");
                    } else {
                        assertEquals(prevRowCol[1], rc[1], "misma columna en toda la cadena vertical");
                        assertEquals(0, rc[1] % 10, "la avenida vertical debe caer en columna multiplo de 10");
                        assertEquals(prevRowCol[0] + idx, rc[0], "filas consecutivas");
                    }
                }
            }
        }
    }

    private int[] parseRowCol(String edgeId) {
        String[] parts = edgeId.split("_");
        return new int[]{Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
    }

    // --- GRIDLOCK (RELIEVE_JUNCTION) ----------------------------------------

    @Test
    void cruceEsUnNodoMayorRealDentroDelDistrito() {
        for (District d : List.of(districtA, districtB)) {
            for (int i = 0; i < RUNS; i++) {
                EventObjective obj = service.buildObjective(d, ObjectiveKind.RELIEVE_JUNCTION, Map.of());
                assertNotNull(obj);
                assertEquals(12, obj.threshold());
                assertEquals(0, obj.current());

                String nodeId = obj.intersectionId();
                assertNotNull(nodeId);
                assertNotNull(cityMap.getNode(nodeId), "node " + nodeId + " deberia existir");

                String[] parts = nodeId.split("_");
                int row = Integer.parseInt(parts[1]);
                int col = Integer.parseInt(parts[2]);
                assertEquals(0, row % 10, "cruce mayor: fila multiplo de 10");
                assertEquals(0, col % 10, "cruce mayor: columna multiplo de 10");

                String nodeZoneId = cityMap.getNode(nodeId).zoneId();
                assertTrue(d.zoneIds().contains(nodeZoneId),
                        "el cruce deberia estar dentro del distrito " + d.index());
            }
        }
    }

    private void assertValidRect(EventObjective obj) {
        assertNotNull(obj.minX());
        assertNotNull(obj.minY());
        assertNotNull(obj.maxX());
        assertNotNull(obj.maxY());
        assertTrue(obj.minX() < obj.maxX());
        assertTrue(obj.minY() < obj.maxY());
        assertTrue(obj.minX() >= 0 && obj.maxX() <= cityMap.getWorldWidth());
        assertTrue(obj.minY() >= 0 && obj.maxY() <= cityMap.getWorldHeight());
        assertTrue(obj.maxX() - obj.minX() <= 30.0 + 1e-9);
        assertTrue(obj.maxY() - obj.minY() <= 30.0 + 1e-9);
    }
}
