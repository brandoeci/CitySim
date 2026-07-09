package edu.escuelaing.citysim.engine.event;

public record EventBroadcast(
        Long eventId,
        String type,
        String status,
        String affectedZoneId,
        String description,
        int durationSeconds,
        int requiredActions,
        int totalActions,
        int progressPercent
) {}