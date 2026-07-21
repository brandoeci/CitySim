package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventObjective;
import edu.escuelaing.citysim.core.model.EventState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EventBroadcaster {

    private static final String GLOBAL_TOPIC = "/topic/events";

    private final SimpMessagingTemplate messaging;
    private final String topic;

    @Autowired
    public EventBroadcaster(SimpMessagingTemplate messaging) {
        this(messaging, GLOBAL_TOPIC);
    }

    /** @param topic p.ej. "/topic/rooms/ABC123/events" para una sala. */
    public EventBroadcaster(SimpMessagingTemplate messaging, String topic) {
        this.messaging = messaging;
        this.topic = topic;
    }

    public void broadcast(EventState event) {
        messaging.convertAndSend(topic, new EventBroadcast(
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
                event.objectives() != null ? event.objectives() : Map.<String, EventObjective>of()
        ));
    }
}