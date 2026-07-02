package edu.escuelaing.citysim.core.pathfinding;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;

import java.util.*;

/** A* pathfinder on the city road graph. Thread-safe (stateless). */
public class AStarPathFinder implements PathFinder {

    private static final double MAX_SPEED = 2.0;

    @Override
    public PathResult findPath(CityMap map, String sourceNodeId, String targetNodeId) {
        if (sourceNodeId.equals(targetNodeId))
            return new PathResult(List.of(sourceNodeId), 0.0);

        Node target = map.getNode(targetNodeId);
        if (target == null || map.getNode(sourceNodeId) == null)
            return new PathResult(List.of(), Double.MAX_VALUE);

        Map<String, Double> gScore = new HashMap<>();
        Map<String, String> cameFrom = new HashMap<>();
        PriorityQueue<ScoredNode> open = new PriorityQueue<>(Comparator.comparingDouble(s -> s.fScore));

        gScore.put(sourceNodeId, 0.0);
        open.add(new ScoredNode(sourceNodeId, heuristic(map.getNode(sourceNodeId), target)));

        while (!open.isEmpty()) {
            ScoredNode current = open.poll();
            if (current.nodeId.equals(targetNodeId))
                return reconstructPath(cameFrom, current.nodeId, gScore.get(current.nodeId));

            double currentG = gScore.getOrDefault(current.nodeId, Double.MAX_VALUE);
            for (Edge edge : map.getOutgoingEdges(current.nodeId)) {
                String neighborId = edge.targetNodeId();
                double tentativeG = currentG + edge.length() / edge.speedLimit();
                if (tentativeG < gScore.getOrDefault(neighborId, Double.MAX_VALUE)) {
                    gScore.put(neighborId, tentativeG);
                    cameFrom.put(neighborId, current.nodeId);
                    double fScore = tentativeG + heuristic(map.getNode(neighborId), target);
                    open.add(new ScoredNode(neighborId, fScore));
                }
            }
        }
        return new PathResult(List.of(), Double.MAX_VALUE);
    }

    private double heuristic(Node from, Node to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        return Math.sqrt(dx * dx + dy * dy) / MAX_SPEED;
    }

    private PathResult reconstructPath(Map<String, String> cameFrom, String current, double cost) {
        LinkedList<String> path = new LinkedList<>();
        String node = current;
        while (node != null) { path.addFirst(node); node = cameFrom.get(node); }
        return new PathResult(List.copyOf(path), cost);
    }

    private record ScoredNode(String nodeId, double fScore) {}
}
