package edu.escuelaing.citysim.engine.traffic;

import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publica el estado de los semaforos por WebSocket.
 *
 * Van por un topic aparte (/topic/lights) y a 1 Hz, no dentro del frame de
 * carros: los semaforos cambian cada varios segundos, no cada 50 ms, y meterlos
 * en cada frame multiplicaria el trafico del socket sin ganar nada.
 *
 * Solo se envian los cruces mayores (donde se encuentran dos avenidas), que son
 * unos cientos y no las decenas de miles de esquinas del grid.
 */
@Component
public class TrafficLightBroadcaster {

    private static final String GLOBAL_TOPIC = "/topic/lights";

    private final TrafficLightController controller;
    private final SimpMessagingTemplate messaging;
    private final SimulationClock clock;
    private final String topic;

    @Autowired
    public TrafficLightBroadcaster(TrafficLightController controller,
                                   SimpMessagingTemplate messaging,
                                   SimulationClock clock) {
        this(controller, messaging, clock, GLOBAL_TOPIC);
    }

    /** @param topic p.ej. "/topic/rooms/ABC123/lights" para una sala. */
    public TrafficLightBroadcaster(TrafficLightController controller,
                                   SimpMessagingTemplate messaging,
                                   SimulationClock clock, String topic) {
        this.controller = controller;
        this.messaging = messaging;
        this.clock = clock;
        this.topic = topic;
    }

    @Scheduled(fixedDelay = 1000L)
    public void broadcast() {
        if (!clock.isRunning()) return;

        List<TrafficLightController.LightView> lights = controller.getMajorLights();
        if (lights.isEmpty()) return;

        messaging.convertAndSend(topic, lights);
    }
}