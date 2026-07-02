package edu.escuelaing.citysim.core.pathfinding;

import edu.escuelaing.citysim.core.map.CityMap;

public interface PathFinder {
    PathResult findPath(CityMap map, String sourceNodeId, String targetNodeId);
}
