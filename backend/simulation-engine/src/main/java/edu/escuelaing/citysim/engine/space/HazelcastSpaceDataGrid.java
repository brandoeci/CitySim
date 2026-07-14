package edu.escuelaing.citysim.engine.space;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class HazelcastSpaceDataGrid implements SpaceDataGrid {

    private static final String ACTIVE_EVENT_KEY = "active";
    private static final int PRESENCE_TTL_SECONDS = 10;

    private final IMap<String, CarState> carMap;
    private final IMap<String, TrafficLightPhase> trafficLightMap;
    private final IMap<String, String> simulationStateMap;
    private final IMap<String, EventState> activeEventMap;
    private final IMap<String, String> zoneAssignmentMap;
    private final IMap<String, Long> activeUserMap;
    private final IMap<String, String> blockedEdgeMap;
    private final ITopic<SimulationFrame> frameTopic;

    public HazelcastSpaceDataGrid(HazelcastInstance hazelcast) {
        this.carMap            = hazelcast.getMap("cars");
        this.trafficLightMap   = hazelcast.getMap("traffic-lights");
        this.simulationStateMap = hazelcast.getMap("simulation-state");
        this.activeEventMap    = hazelcast.getMap("active-events");
        this.zoneAssignmentMap = hazelcast.getMap("zone-assignments");
        this.activeUserMap     = hazelcast.getMap("active-users");
        this.blockedEdgeMap    = hazelcast.getMap("blocked-edges");
        this.frameTopic        = hazelcast.getTopic("sim-frames");
    }

    // Cars
    @Override public void putCar(CarState car)          { carMap.set(car.getCarId(), car); }
    @Override public CarState getCar(String carId)      { return carMap.get(carId); }
    @Override public void removeCar(String carId)       { carMap.delete(carId); }
    @Override public long getCarCount()                 { return carMap.size(); }

    @Override
    public Collection<CarState> getCarsInZone(String zoneId) {
        return carMap.values().stream()
                .filter(c -> zoneId.equals(c.getCurrentZoneId()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, CarState> getAllCars() {
        Map<String, CarState> snapshot = new HashMap<>();
        carMap.forEach(snapshot::put);
        return snapshot;
    }

    // Traffic lights
    @Override
    public void putTrafficLight(TrafficLightPhase phase) {
        trafficLightMap.set(phase.getIntersectionId(), phase);
    }

    @Override
    public TrafficLightPhase getTrafficLight(String intersectionId) {
        return trafficLightMap.get(intersectionId);
    }

    // Frames
    @Override
    public void publishFrame(SimulationFrame frame) {
        frameTopic.publish(frame);
    }

    // General state
    @Override
    public void putSimulationState(String key, String value) {
        simulationStateMap.set(key, value);
    }

    @Override
    public String getSimulationState(String key) {
        return simulationStateMap.get(key);
    }

    // Eventos colaborativos
    @Override
    public void putActiveEvent(EventState event) {
        activeEventMap.set(ACTIVE_EVENT_KEY, event);
    }

    @Override
    public EventState getActiveEvent() {
        return activeEventMap.get(ACTIVE_EVENT_KEY);
    }

    @Override
    public void clearActiveEvent() {
        activeEventMap.delete(ACTIVE_EVENT_KEY);
    }

    // Asignaciones de zona
    @Override
    public void assignZone(String username, String zoneId) {
        zoneAssignmentMap.putIfAbsent(username, zoneId);
    }

    @Override
    public String getAssignedZone(String username) {
        return zoneAssignmentMap.get(username);
    }

    @Override
    public Map<String, String> getAllZoneAssignments() {
        Map<String, String> snapshot = new HashMap<>();
        zoneAssignmentMap.forEach(snapshot::put);
        return snapshot;
    }

    // Presencia con TTL
    @Override
    public void heartbeat(String username) {
        Long firstSeen = activeUserMap.get(username);
        long value = (firstSeen != null) ? firstSeen : System.currentTimeMillis();
        activeUserMap.set(username, value, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void removePresence(String username) {
        activeUserMap.delete(username);
    }

    @Override
    public boolean isActive(String username) {
        return activeUserMap.containsKey(username);
    }

    @Override
    public int getActiveUserCount() {
        return activeUserMap.size();
    }

    @Override
    public List<String> getActiveUsersOrdered() {
        return activeUserMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // Vias cerradas: edgeId -> username que la cerro.
    @Override
    public void blockEdge(String edgeId, String username) {
        blockedEdgeMap.set(edgeId, username);
    }

    @Override
    public void unblockEdge(String edgeId) {
        blockedEdgeMap.delete(edgeId);
    }

    @Override
    public boolean isEdgeBlocked(String edgeId) {
        return blockedEdgeMap.containsKey(edgeId);
    }

    @Override
    public Set<String> getBlockedEdges() {
        return new HashSet<>(blockedEdgeMap.keySet());
    }

    @Override
    public Map<String, String> getBlockedEdgesWithOwner() {
        Map<String, String> snapshot = new HashMap<>();
        blockedEdgeMap.forEach(snapshot::put);
        return snapshot;
    }

    // Acceso directo para listeners
    public IMap<String, CarState> getCarMap()      { return carMap; }
    public ITopic<SimulationFrame> getFrameTopic() { return frameTopic; }
}