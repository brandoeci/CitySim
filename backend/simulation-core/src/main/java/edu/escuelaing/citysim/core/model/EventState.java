package edu.escuelaing.citysim.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        Map<String, Integer> actionsByZone
) implements Serializable {

    public EventState withAction(String zoneId) {
        Map<String, Integer> updated = new HashMap<>(actionsByZone);
        updated.merge(zoneId, 1, Integer::sum);
        return new EventState(eventId, type, status, affectedZoneId, description,
                durationSeconds, requiredActions, startedAt, resolvedAt,
                Collections.unmodifiableMap(updated));
    }

    public EventState withStatus(String newStatus, Instant resolvedAt) {
        return new EventState(eventId, type, newStatus, affectedZoneId, description,
                durationSeconds, requiredActions, startedAt, resolvedAt,
                Collections.unmodifiableMap(actionsByZone));
    }

    public int totalActions() {
        return actionsByZone.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int progressPercent() {
        return requiredActions > 0
                ? Math.min(100, totalActions() * 100 / requiredActions) : 0;
    }

    public boolean isResolved() {
        return totalActions() >= requiredActions;
    }
}
