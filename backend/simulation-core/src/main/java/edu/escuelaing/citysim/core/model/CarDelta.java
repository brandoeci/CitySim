package edu.escuelaing.citysim.core.model;

import java.io.Serializable;

public record CarDelta(
        String carId,
        double x,
        double y,
        double heading,
        String status,
        String color
) implements Serializable {}
