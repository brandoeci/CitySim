package edu.escuelaing.citysim.engine.room;

import com.hazelcast.core.HazelcastInstance;
import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.pathfinding.PathFinder;
import edu.escuelaing.citysim.engine.car.CarAgent;
import edu.escuelaing.citysim.engine.car.CarSpawner;
import edu.escuelaing.citysim.engine.car.PopulationMaintainer;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.event.EventBroadcaster;
import edu.escuelaing.citysim.engine.event.EventGeneratorLeader;
import edu.escuelaing.citysim.engine.event.EventObjectiveTracker;
import edu.escuelaing.citysim.engine.event.EventRepository;
import edu.escuelaing.citysim.engine.event.EventService;
import edu.escuelaing.citysim.engine.simulation.FramePublisher;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import edu.escuelaing.citysim.engine.simulation.SimulationOrchestrator;
import edu.escuelaing.citysim.engine.space.HazelcastSpaceDataGrid;
import edu.escuelaing.citysim.engine.traffic.TrafficLightBroadcaster;
import edu.escuelaing.citysim.engine.traffic.TrafficLightController;
import edu.escuelaing.citysim.engine.zone.DistrictService;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Todos los componentes de simulacion de UNA sala, instanciados a mano (NO
 * beans de Spring): su propio SpaceDataGrid prefijado, ZoneRegistry,
 * TrafficLightController, reloj y scheduler. Cada sala corre en sus propios
 * hilos, en paralelo real con las demas -- no hay un iterador central
 * repartiendo una ventana de tick entre salas.
 *
 * El mapa (CityMap) y la logica pura (CarAgent, PathFinder) SI se comparten
 * entre salas: son iguales para todas y no tienen estado mutable.
 */
public class RoomSimulation {

    private static final Logger log = LoggerFactory.getLogger(RoomSimulation.class);
    private static final int INITIAL_CARS = 200;

    private final String code;
    private final SimpMessagingTemplate messaging;

    private final HazelcastSpaceDataGrid space;
    private final TrafficLightController trafficController;
    private final EventGeneratorLeader leader;
    private final EventObjectiveTracker eventTracker;
    private final DistrictService districtService;
    private final EventService eventService;
    private final ZoneRegistry zoneRegistry;
    private final SimulationOrchestrator orchestrator;
    private final SimulationClock clock;
    private final CarSpawner carSpawner;
    private final PopulationMaintainer populationMaintainer;
    private final TrafficLightBroadcaster lightBroadcaster;

    private ScheduledExecutorService scheduler;
    private UUID frameListenerId;

    /** Cooldown de LLUVIA DE TRAFICO: username -> ultima vez usado (epoch ms). En memoria, propio de esta sala. */
    private final Map<String, Long> trafficBombCooldowns = new ConcurrentHashMap<>();

    /** Cooldown de ESCUDO DE DISTRITO: username -> ultima vez usado (epoch ms). En memoria, propio de esta sala. */
    private final Map<String, Long> districtShieldCooldowns = new ConcurrentHashMap<>();

    public RoomSimulation(String code, HazelcastInstance hazelcast, CityMap cityMap,
                          CarAgent carAgent, PathFinder pathFinder, SimulationProperties props,
                          SimpMessagingTemplate messaging, EventRepository eventRepository) {
        this.code = code;
        this.messaging = messaging;

        String prefix = "room:" + code + ":";
        this.space = new HazelcastSpaceDataGrid(hazelcast, prefix);
        this.trafficController = new TrafficLightController(cityMap);
        this.leader = new EventGeneratorLeader(hazelcast, prefix);

        EventBroadcaster eventBroadcaster = new EventBroadcaster(messaging, "/topic/rooms/" + code + "/events");
        this.eventTracker = new EventObjectiveTracker(hazelcast, space, eventBroadcaster, eventRepository, leader, prefix);
        this.districtService = new DistrictService(cityMap, space);
        this.eventService = new EventService(eventRepository, eventBroadcaster, space, leader,
                districtService, cityMap, eventTracker);

        this.zoneRegistry = new ZoneRegistry(
                hazelcast, space, cityMap, carAgent, trafficController, props, eventTracker, prefix);

        FramePublisher framePublisher = new FramePublisher(space);
        this.orchestrator = new SimulationOrchestrator(zoneRegistry, space, framePublisher);
        this.clock = new SimulationClock(orchestrator, props);
        this.carSpawner = new CarSpawner(space, cityMap, pathFinder, props);
        this.populationMaintainer = new PopulationMaintainer(space, carSpawner, clock, leader, props);
        this.lightBroadcaster = new TrafficLightBroadcaster(
                trafficController, messaging, clock, "/topic/rooms/" + code + "/lights");
    }

    public void start() {
        zoneRegistry.initialize();

        frameListenerId = space.getFrameTopic().addMessageListener(msg ->
                messaging.convertAndSend("/topic/rooms/" + code + "/cars", msg.getMessageObject()));

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "room-" + code);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(leader::renewOrAcquire, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(populationMaintainer::maintain, 2, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(eventTracker::evaluate, 1, 1, TimeUnit.SECONDS);
        // 2s, no 120s: en cuanto un evento se resuelve/expira/falla se borra
        // de inmediato del space, asi que el proximo chequeo ya dispara el
        // siguiente evento -- eventos seguidos, sin pausa de calma entre ellos.
        scheduler.scheduleWithFixedDelay(eventService::generateEvent, 5, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(lightBroadcaster::broadcast, 1, 1, TimeUnit.SECONDS);

        clock.start();
        carSpawner.spawn(INITIAL_CARS);
        log.info("Sala {} arrancada", code);
    }

    public void stop() {
        clock.stop();
        if (scheduler != null) scheduler.shutdownNow();
        if (frameListenerId != null) space.getFrameTopic().removeMessageListener(frameListenerId);
        log.info("Sala {} detenida", code);
    }

    public String getCode() { return code; }
    public HazelcastSpaceDataGrid getSpace() { return space; }
    public DistrictService getDistrictService() { return districtService; }
    public EventService getEventService() { return eventService; }
    public TrafficLightController getTrafficLightController() { return trafficController; }
    public SimulationClock getClock() { return clock; }
    public CarSpawner getCarSpawner() { return carSpawner; }
    public ZoneRegistry getZoneRegistry() { return zoneRegistry; }
    public SimulationOrchestrator getOrchestrator() { return orchestrator; }
    public PopulationMaintainer getPopulationMaintainer() { return populationMaintainer; }
    public Map<String, Long> getTrafficBombCooldowns() { return trafficBombCooldowns; }
    public Map<String, Long> getDistrictShieldCooldowns() { return districtShieldCooldowns; }
}
