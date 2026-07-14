package edu.escuelaing.citysim.engine.event;

import java.util.Map;
import java.util.Set;

public record EventBroadcast(
        Long eventId,
        String type,
        String status,
        String affectedZoneId,
        String description,
        int durationSeconds,
        int requiredActions,
        int totalActions,
        int progressPercent,
        String startedAt,
        Map<String, String> targetEdges,
        Set<String> respondedBy
) {}