package edu.escuelaing.citysim.core.map;

import java.io.Serializable;
import java.util.*;

public class CityMap implements Serializable {

    private final Map<String, Node> nodes;
    private final Map<String, Edge> edges;
    private final Map<String, Zone> zones;
    private final int gridWidth;
    private final int gridHeight;
    private final double worldWidth;
    private final double worldHeight;

    public CityMap(Map<String, Node> nodes, Map<String, Edge> edges,
                   Map<String, Zone> zones, int gridWidth, int gridHeight) {
        this.nodes = Collections.unmodifiableMap(nodes);
        this.edges = Collections.unmodifiableMap(edges);
        this.zones = Collections.unmodifiableMap(zones);
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.worldWidth = (gridWidth - 1) * 10.0;
        this.worldHeight = (gridHeight - 1) * 10.0;
    }

    public Map<String, Node> getNodes()  { return nodes; }
    public Map<String, Edge> getEdges()  { return edges; }
    public Map<String, Zone> getZones()  { return zones; }
    public int getGridWidth()            { return gridWidth; }
    public int getGridHeight()           { return gridHeight; }
    public double getWorldWidth()        { return worldWidth; }
    public double getWorldHeight()       { return worldHeight; }

    public Node getNode(String id)  { return nodes.get(id); }
    public Edge getEdge(String id)  { return edges.get(id); }
    public Zone getZone(String id)  { return zones.get(id); }

    public List<Edge> getOutgoingEdges(String nodeId) {
        Node node = nodes.get(nodeId);
        if (node == null) return List.of();
        List<Edge> result = new ArrayList<>(node.outgoingEdgeIds().size());
        for (String eid : node.outgoingEdgeIds()) {
            Edge e = edges.get(eid);
            if (e != null) result.add(e);
        }
        return result;
    }
}
