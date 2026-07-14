package edu.escuelaing.citysim.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Evento colaborativo en curso.
 *
 * @param targetEdges  zoneId -> id de la via que ese distrito debe intervenir.
 *                     El evento afecta a toda la ciudad: cada administrador
 *                     tiene su propio objetivo dentro de su territorio, y el
 *                     evento solo se resuelve si TODOS actuan.
 * @param respondedBy  zoneIds que ya cumplieron su objetivo.
 */
public record EventState(
        Long eventId,
        String type,
        String status,
        String affectedZoneId,
        String description,
        int durationSeconds,
        int requiredActions,
        Instant startedAt,
        Instant resolvedAt,
        Map<String, Integer> actionsByZone,
        Map<String, String> targetEdges,
        java.util.Set<String> respondedBy
) implements Serializable {

    /** Registra que un distrito cumplio su objetivo. Idempotente: no cuenta dos veces. */
    public EventState withAction(String zoneId) {
        if (respondedBy.contains(zoneId)) return this;   // ya respondio

        Map<String, Integer> updated = new HashMap<>(actionsByZone);
        updated.merge(zoneId, 1, Integer::sum);

        java.util.Set<String> responded = new java.util.HashSet<>(respondedBy);
        responded.add(zoneId);

        return new EventState(eventId, type, status, affectedZoneId, description,
                durationSeconds, requiredActions, startedAt, resolvedAt,
                Collections.unmodifiableMap(updated),
                targetEdges,
                Collections.unmodifiableSet(responded));
    }

    public EventState withStatus(String newStatus, Instant resolvedAt) {
        return new EventState(eventId, type, newStatus, affectedZoneId, description,
                durationSeconds, requiredActions, startedAt, resolvedAt,
                Collections.unmodifiableMap(actionsByZone),
                targetEdges,
                Collections.unmodifiableSet(respondedBy));
    }

    /** La via que este distrito debe intervenir, o null si no le toca ninguna. */
    public String targetEdgeFor(String zoneId) {
        return targetEdges != null ? targetEdges.get(zoneId) : null;
    }

    public boolean hasResponded(String zoneId) {
        return respondedBy != null && respondedBy.contains(zoneId);
    }

    /** Numero de distritos que ya cumplieron su objetivo. */
    public int totalActions() {
        return respondedBy != null ? respondedBy.size() : 0;
    }

    public int progressPercent() {
        return requiredActions > 0
                ? Math.min(100, totalActions() * 100 / requiredActions) : 0;
    }

    /** El evento se resuelve solo cuando TODOS los distritos activos respondieron. */
    public boolean isResolved() {
        return totalActions() >= requiredActions;
    }
}