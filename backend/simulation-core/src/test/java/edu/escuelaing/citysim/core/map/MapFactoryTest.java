package edu.escuelaing.citysim.core.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapFactoryTest {

    @Test
    void generaElNumeroCorrectoDeNodos() {
        // Grid 10x10 = 100 nodos
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        assertEquals(100, map.getNodes().size());
    }

    @Test
    void generaElNumeroCorrectoDeZonas() {
        // 2x2 = 4 zonas
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        assertEquals(4, map.getZones().size());
    }

    @Test
    void lasZonasTienenLosIdsEsperados() {
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        assertNotNull(map.getZone("Z_0_0"));
        assertNotNull(map.getZone("Z_0_1"));
        assertNotNull(map.getZone("Z_1_0"));
        assertNotNull(map.getZone("Z_1_1"));
    }

    @Test
    void todaAristaApuntaANodosExistentes() {
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        for (Edge edge : map.getEdges().values()) {
            assertNotNull(map.getNode(edge.sourceNodeId()),
                    "Arista con source inexistente: " + edge.id());
            assertNotNull(map.getNode(edge.targetNodeId()),
                    "Arista con target inexistente: " + edge.id());
        }
    }

    @Test
    void getOutgoingEdgesDevuelveLasAristasDelNodo() {
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        // Un nodo interno debe tener aristas salientes
        List<Edge> outgoing = map.getOutgoingEdges("N_5_5");
        assertFalse(outgoing.isEmpty());
        // Todas deben salir de ese nodo
        for (Edge e : outgoing) {
            assertEquals("N_5_5", e.sourceNodeId());
        }
    }

    @Test
    void elNodoEsquinaTieneMenosAristasQueUnoInterno() {
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        int corner = map.getOutgoingEdges("N_0_0").size();
        int inner  = map.getOutgoingEdges("N_5_5").size();
        assertTrue(corner < inner,
                "La esquina deberia tener menos salidas que un nodo interno");
    }

    @Test
    void lasAristasEnFilasMultiplosDe10SonAutopista() {
        CityMap map = MapFactory.generate(20, 20, 2, 2);
        // La fila 0 es autopista (0 % 10 == 0). Arista horizontal en fila 0.
        Edge highwayEdge = map.getEdge("E_H_0_0");
        assertNotNull(highwayEdge);
        assertTrue(highwayEdge.isHighway());
        assertEquals(2.0, highwayEdge.speedLimit());
        assertEquals(4, highwayEdge.laneCount());
    }

    @Test
    void lasAristasEnFilasNormalesSonCalles() {
        CityMap map = MapFactory.generate(20, 20, 2, 2);
        // La fila 5 no es autopista. Arista horizontal en fila 5.
        Edge streetEdge = map.getEdge("E_H_5_0");
        assertNotNull(streetEdge);
        assertFalse(streetEdge.isHighway());
        assertEquals(1.0, streetEdge.speedLimit());
        assertEquals(2, streetEdge.laneCount());
    }

    @Test
    void lasDimensionesDelMundoSonCorrectas() {
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        assertEquals(10, map.getGridWidth());
        assertEquals(10, map.getGridHeight());
        // worldWidth = (gridWidth - 1) * 10
        assertEquals(90.0, map.getWorldWidth());
        assertEquals(90.0, map.getWorldHeight());
    }

    @Test
    void cadaNodoPerteneceAUnaZonaValida() {
        CityMap map = MapFactory.generate(10, 10, 2, 2);
        for (Node node : map.getNodes().values()) {
            assertNotNull(map.getZone(node.zoneId()),
                    "Nodo con zona inexistente: " + node.id());
        }
    }
}
