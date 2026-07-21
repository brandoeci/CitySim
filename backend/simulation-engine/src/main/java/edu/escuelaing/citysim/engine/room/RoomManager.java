package edu.escuelaing.citysim.engine.room;

import com.hazelcast.core.HazelcastInstance;
import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.pathfinding.PathFinder;
import edu.escuelaing.citysim.engine.car.CarAgent;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.event.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crea y destruye las RoomSimulation de cada sala activa. Una sola instancia
 * de backend (ver plan de Fase 2): no hay eleccion de "quien levanta la sala"
 * entre varias replicas, la instancia que la crea la corre entera.
 */
@Service
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final HazelcastInstance hazelcast;
    private final CityMap cityMap;
    private final CarAgent carAgent;
    private final PathFinder pathFinder;
    private final SimulationProperties props;
    private final SimpMessagingTemplate messaging;
    private final EventRepository eventRepository;

    private final Map<String, RoomSimulation> activeRooms = new ConcurrentHashMap<>();

    public RoomManager(HazelcastInstance hazelcast, CityMap cityMap, CarAgent carAgent,
                       PathFinder pathFinder, SimulationProperties props,
                       SimpMessagingTemplate messaging, EventRepository eventRepository) {
        this.hazelcast = hazelcast;
        this.cityMap = cityMap;
        this.carAgent = carAgent;
        this.pathFinder = pathFinder;
        this.props = props;
        this.messaging = messaging;
        this.eventRepository = eventRepository;
    }

    /** Idempotente: si la sala ya esta corriendo, devuelve la misma instancia. */
    public RoomSimulation startRoom(String code) {
        return activeRooms.computeIfAbsent(code, c -> {
            RoomSimulation sim = new RoomSimulation(c, hazelcast, cityMap, carAgent, pathFinder,
                    props, messaging, eventRepository);
            sim.start();
            log.info("RoomManager arranco la sala {}", c);
            return sim;
        });
    }

    public void stopRoom(String code) {
        RoomSimulation sim = activeRooms.remove(code);
        if (sim != null) sim.stop();
    }

    public RoomSimulation getRoom(String code) {
        return activeRooms.get(code);
    }
}
