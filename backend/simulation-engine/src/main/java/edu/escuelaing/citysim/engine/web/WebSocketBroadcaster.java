package edu.escuelaing.citysim.engine.web;

import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.engine.space.HazelcastSpaceDataGrid;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Hazelcast ITopic "sim-frames" and broadcasts each frame
 * to all WebSocket clients connected to this backend instance via STOMP.
 *
 * Thread-safety: SimpMessagingTemplate.convertAndSend is thread-safe.
 */
@Component
public class WebSocketBroadcaster implements MessageListener<SimulationFrame> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);
    private static final String CARS_TOPIC = "/topic/cars";

    private final SimpMessagingTemplate messaging;
    private final HazelcastSpaceDataGrid space;

    public WebSocketBroadcaster(SimpMessagingTemplate messaging, HazelcastSpaceDataGrid space) {
        this.messaging = messaging;
        this.space = space;
    }

    @PostConstruct
    public void subscribe() {
        space.getFrameTopic().addMessageListener(this);
        log.info("WebSocketBroadcaster subscribed to Hazelcast sim-frames topic");
    }

    @Override
    public void onMessage(Message<SimulationFrame> message) {
        SimulationFrame frame = message.getMessageObject();
        try {
            messaging.convertAndSend(CARS_TOPIC, frame);
        } catch (Exception e) {
            log.warn("Failed to broadcast frame {}: {}", frame.frameId(), e.getMessage());
        }
    }
}
