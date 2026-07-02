package edu.escuelaing.citysim.core.model;

import java.io.Serializable;
import java.util.List;

public record SimulationFrame(
        long frameId,
        long timestamp,
        List<CarDelta> deltas,
        List<String> removedCarIds,
        int totalCars,
        double fps
) implements Serializable {}
