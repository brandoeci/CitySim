package edu.escuelaing.citysim.core.map;

import java.io.Serializable;
import java.util.Set;

public record Zone(
        String id,
        int zoneRow,
        int zoneCol,
        double minX,
        double minY,
        double maxX,
        double maxY,
        Set<String> nodeIds,
        Set<String> edgeIds
) implements Serializable {}
