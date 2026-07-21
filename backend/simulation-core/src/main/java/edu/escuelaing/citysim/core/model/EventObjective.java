package edu.escuelaing.citysim.core.model;

import java.io.Serializable;
import java.util.List;

/**
 * Objetivo de un distrito dentro de un evento colaborativo.
 *
 * Segun {@code kind}, solo algunos campos aplican:
 * <ul>
 *   <li>CLOSE_EDGE: {@code edgeIds} tiene un solo elemento, la via a cerrar.</li>
 *   <li>SHIELD_AREA / EVACUATE_AREA: {@code minX,minY,maxX,maxY} delimitan el area.</li>
 *   <li>CLEAR_CORRIDOR: {@code edgeIds} es la cadena de tramos del corredor.</li>
 *   <li>RELIEVE_JUNCTION: {@code intersectionId} es el nodo del cruce.</li>
 * </ul>
 *
 * {@code threshold} es fijo (se define al generar el evento): tolerancia
 * inicial, segundos requeridos, umbral de cola o de vehiculos. {@code current}
 * es el contador vivo, recalculado por el tracker en cada evaluacion.
 */
public record EventObjective(
        String zoneId,
        ObjectiveKind kind,
        List<String> edgeIds,
        Double minX,
        Double minY,
        Double maxX,
        Double maxY,
        String intersectionId,
        int threshold,
        int current,
        boolean completed,
        boolean failed
) implements Serializable {

    public EventObjective withCurrent(int newCurrent) {
        return new EventObjective(zoneId, kind, edgeIds, minX, minY, maxX, maxY,
                intersectionId, threshold, newCurrent, completed, failed);
    }

    public EventObjective withCompleted() {
        if (completed) return this;
        return new EventObjective(zoneId, kind, edgeIds, minX, minY, maxX, maxY,
                intersectionId, threshold, current, true, failed);
    }

    public EventObjective withFailed() {
        if (failed) return this;
        return new EventObjective(zoneId, kind, edgeIds, minX, minY, maxX, maxY,
                intersectionId, threshold, current, completed, true);
    }

    /** La via objetivo, solo valida para CLOSE_EDGE. */
    public String targetEdge() {
        return (edgeIds != null && !edgeIds.isEmpty()) ? edgeIds.get(0) : null;
    }
}
