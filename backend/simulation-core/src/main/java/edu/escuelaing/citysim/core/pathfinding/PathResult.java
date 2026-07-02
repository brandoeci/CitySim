package edu.escuelaing.citysim.core.pathfinding;

import java.util.List;

public record PathResult(List<String> nodeIds, double totalCost) {
    public boolean isFound() {
        return nodeIds != null && nodeIds.size() >= 2;
    }
}
