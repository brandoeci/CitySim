package edu.escuelaing.citysim.core.map;

import java.io.Serializable;
import java.util.List;

public record Node(
        String id,
        double x,
        double y,
        String zoneId,
        boolean isIntersection,
        List<String> outgoingEdgeIds,
        List<String> incomingEdgeIds
) implements Serializable {}
