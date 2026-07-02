package edu.escuelaing.citysim.core.map;

import java.io.Serializable;

public record Edge(
        String id,
        String sourceNodeId,
        String targetNodeId,
        double length,
        double speedLimit,
        int laneCount,
        boolean isHighway,
        String zoneId
) implements Serializable {}
