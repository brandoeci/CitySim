package edu.escuelaing.citysim.core.pathfinding;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AStarPathFinderTest {

    private final AStarPathFinder finder = new AStarPathFinder();

    private CityMap buildLinearMap() {
        Map<String, Node> nodes = new HashMap<>();
        Map<String, Edge> edges = new HashMap<>();

        nodes.put("N1", new Node("N1", 0, 0, "Z0", true, List.of("E12"), List.of()));
        nodes.put("N2", new Node("N2", 10, 0, "Z0", true, List.of("E23"), List.of("E12")));
        nodes.put("N3", new Node("N3", 20, 0, "Z0", true, List.of(), List.of("E23")));

        edges.put("E12", new Edge("E12", "N1", "N2", 10, 1.0, 1, false, "Z0"));
        edges.put("E23", new Edge("E23", "N2", "N3", 10, 1.0, 1, false, "Z0"));

        return new CityMap(nodes, edges, Map.of(), 3, 1);
    }

    @Test
    void encuentraRutaEntreNodosConectados() {
        CityMap map = buildLinearMap();
        PathResult result = finder.findPath(map, "N1", "N3");

        assertTrue(result.isFound());
        assertEquals(List.of("N1", "N2", "N3"), result.nodeIds());
    }

    @Test
    void origenIgualADestinoDevuelveRutaTrivial() {
        CityMap map = buildLinearMap();
        PathResult result = finder.findPath(map, "N1", "N1");

        assertEquals(List.of("N1"), result.nodeIds());
        assertEquals(0.0, result.totalCost());
        assertFalse(result.isFound());
    }

    @Test
    void nodosSinConexionDevuelveNoEncontrado() {
        Map<String, Node> nodes = new HashMap<>();
        Map<String, Edge> edges = new HashMap<>();
        nodes.put("A", new Node("A", 0, 0, "Z0", true, List.of(), List.of()));
        nodes.put("B", new Node("B", 100, 100, "Z0", true, List.of(), List.of()));
        CityMap map = new CityMap(nodes, edges, Map.of(), 2, 1);

        PathResult result = finder.findPath(map, "A", "B");

        assertFalse(result.isFound());
        assertEquals(Double.MAX_VALUE, result.totalCost());
    }

    @Test
    void destinoInexistenteDevuelveNoEncontrado() {
        CityMap map = buildLinearMap();
        PathResult result = finder.findPath(map, "N1", "NO_EXISTE");

        assertFalse(result.isFound());
    }

    @Test
    void origenInexistenteDevuelveNoEncontrado() {
        CityMap map = buildLinearMap();
        PathResult result = finder.findPath(map, "NO_EXISTE", "N3");

        assertFalse(result.isFound());
    }

    @Test
    void eligeLaRutaMasBarataCuandoHayVariasOpciones() {
        Map<String, Node> nodes = new HashMap<>();
        Map<String, Edge> edges = new HashMap<>();

        nodes.put("N1", new Node("N1", 0, 0, "Z0", true, List.of("E12", "E13"), List.of()));
        nodes.put("N2", new Node("N2", 10, 0, "Z0", true, List.of("E23"), List.of("E12")));
        nodes.put("N3", new Node("N3", 20, 0, "Z0", true, List.of(), List.of("E23", "E13")));

        edges.put("E12", new Edge("E12", "N1", "N2", 10, 2.0, 1, false, "Z0"));
        edges.put("E23", new Edge("E23", "N2", "N3", 10, 2.0, 1, false, "Z0"));
        edges.put("E13", new Edge("E13", "N1", "N3", 20, 0.1, 1, false, "Z0"));

        CityMap map = new CityMap(nodes, edges, Map.of(), 3, 1);
        PathResult result = finder.findPath(map, "N1", "N3");

        assertEquals(List.of("N1", "N2", "N3"), result.nodeIds());
    }

    @Test
    void elCostoDeLaRutaEsPositivo() {
        CityMap map = buildLinearMap();
        PathResult result = finder.findPath(map, "N1", "N3");

        assertTrue(result.totalCost() > 0);
        assertTrue(result.totalCost() < Double.MAX_VALUE);
    }
}
