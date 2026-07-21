package edu.escuelaing.citysim.core.model;

import java.io.Serializable;

/**
 * Multiplicador de velocidad temporal sobre una via (REDUCTOR o TURBO).
 *
 * @param expiresAtTick tick de simulacion en el que deja de aplicar; se
 *                       compara contra el tickNumber que ya recibe
 *                       CarAgent.advance() en cada tick real.
 */
public record SpeedOverride(String edgeId, double factor, long expiresAtTick, String placedBy)
        implements Serializable {
}
