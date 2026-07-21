package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventObjective;

import java.util.Map;

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
        Map<String, EventObjective> objectives
) {}
