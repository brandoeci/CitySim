package edu.escuelaing.citysim.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Evento colaborativo en curso.
 *
 * @param objectives  zoneId -> objetivo que ese distrito debe cumplir. El
 *                     evento afecta a toda la ciudad: cada administrador
 *                     tiene su propio objetivo dentro de su territorio, y el
 *                     evento solo se resuelve si TODOS cumplen (o, para los
 *                     eventos de supervivencia, si ninguno falla antes de que
 *                     se acabe el tiempo).
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
        Map<String, EventObjective> objectives
) implements Serializable {

    /** Reemplaza el objetivo de un distrito (p.ej. tras recalcular su contador). */
    public EventState withObjective(String zoneId, EventObjective updated) {
        EventObjective existing = objectives.get(zoneId);
        if (existing != null && existing.completed() && updated.completed()
                && existing.current() == updated.current() && existing.failed() == updated.failed()) {
            return this;
        }
        Map<String, EventObjective> merged = new HashMap<>(objectives);
        merged.put(zoneId, updated);
        return new EventState(eventId, type, status, affectedZoneId, description,
                durationSeconds, requiredActions, startedAt, resolvedAt,
                Collections.unmodifiableMap(merged));
    }

    public EventState withStatus(String newStatus, Instant resolvedAt) {
        return new EventState(eventId, type, newStatus, affectedZoneId, description,
                durationSeconds, requiredActions, startedAt, resolvedAt,
                objectives);
    }

    /** El objetivo de este distrito, o null si no le toca ninguno. */
    public EventObjective objectiveFor(String zoneId) {
        return objectives != null ? objectives.get(zoneId) : null;
    }

    public boolean hasResponded(String zoneId) {
        EventObjective o = objectiveFor(zoneId);
        return o != null && o.completed();
    }

    /** Numero de distritos que ya cumplieron su objetivo. */
    public int totalActions() {
        if (objectives == null) return 0;
        return (int) objectives.values().stream().filter(EventObjective::completed).count();
    }

    public int progressPercent() {
        return requiredActions > 0
                ? Math.min(100, totalActions() * 100 / requiredActions) : 0;
    }

    /** El evento se resuelve solo cuando TODOS los distritos activos cumplieron. */
    public boolean isResolved() {
        return totalActions() >= requiredActions;
    }

    /** True si algun distrito fallo su objetivo (eventos de supervivencia). */
    public boolean isFailed() {
        return objectives != null && objectives.values().stream().anyMatch(EventObjective::failed);
    }
}
