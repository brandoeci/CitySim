package edu.escuelaing.citysim.core.pathfinding;

import edu.escuelaing.citysim.core.map.CityMap;

import java.util.Set;

public interface PathFinder {

    /** Ruta sin restricciones. */
    PathResult findPath(CityMap map, String sourceNodeId, String targetNodeId);

    /**
     * Ruta evitando las vias cerradas por los administradores de zona.
     * Si no hay ruta alterna, devuelve un PathResult no encontrado.
     */
    PathResult findPath(CityMap map, String sourceNodeId, String targetNodeId,
                        Set<String> blockedEdgeIds);
}