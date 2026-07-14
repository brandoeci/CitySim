package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class EventBroadcaster {

    private static final String TOPIC = "/topic/events";

    private final SimpMessagingTemplate messaging;

    public EventBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void broadcast(EventState event) {
        messaging.convertAndSend(TOPIC, new EventBroadcast(
                event.eventId(),
                event.type(),
                event.status(),
                event.affectedZoneId(),
                event.description(),
                event.durationSeconds(),
                event.requiredActions(),
                event.totalActions(),
                event.progressPercent(),
                event.startedAt() != null ? event.startedAt().toString() : null,
                event.targetEdges() != null ? event.targetEdges() : Map.of(),
                event.respondedBy() != null ? event.respondedBy() : Set.of()
        ));
    }
}