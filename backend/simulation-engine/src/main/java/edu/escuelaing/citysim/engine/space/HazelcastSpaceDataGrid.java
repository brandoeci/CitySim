package edu.escuelaing.citysim.engine.space;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HazelcastSpaceDataGrid implements SpaceDataGrid {

    private final IMap<String, CarState> carMap;
    private final IMap<String, TrafficLightPhase> trafficLightMap;
    private final IMap<String, String> simulationStateMap;
    private final ITopic<SimulationFrame> frameTopic;

    public HazelcastSpaceDataGrid(HazelcastInstance hazelcast) {
        this.carMap = hazelcast.getMap("cars");
        this.trafficLightMap = hazelcast.getMap("traffic-lights");
        this.simulationStateMap = hazelcast.getMap("simulation-state");
        this.frameTopic = hazelcast.getTopic("sim-frames");
    }

    @Override
    public void putCar(CarState car) {
        carMap.set(car.getCarId(), car);
    }

    @Override
    public CarState getCar(String carId) {
        return carMap.get(carId);
    }

    @Override
    public void removeCar(String carId) {
        carMap.delete(carId);
    }

    @Override
    public Collection<CarState> getCarsInZone(String zoneId) {
        // Client-side filter: BINARY in-memory format means server can't evaluate predicates
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

    @Override
    public long getCarCount() {
        return carMap.size();
    }

    @Override
    public void putTrafficLight(TrafficLightPhase phase) {
        trafficLightMap.set(phase.getIntersectionId(), phase);
    }

    @Override
    public TrafficLightPhase getTrafficLight(String intersectionId) {
        return trafficLightMap.get(intersectionId);
    }

    @Override
    public void publishFrame(SimulationFrame frame) {
        frameTopic.publish(frame);
    }

    @Override
    public void putSimulationState(String key, String value) {
        simulationStateMap.set(key, value);
    }

    @Override
    public String getSimulationState(String key) {
        return simulationStateMap.get(key);
    }

    public IMap<String, CarState> getCarMap() {
        return carMap;
    }

    public ITopic<SimulationFrame> getFrameTopic() {
        return frameTopic;
    }
}
