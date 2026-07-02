package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.model.CarDelta;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.sba.ProcessingUnit;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.car.CarAgent;
import edu.escuelaing.citysim.engine.car.CollisionAvoider;
import edu.escuelaing.citysim.engine.traffic.TrafficLightController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** SBA Processing Unit: manages all cars currently in one city zone. */
public class ZoneProcessingUnit implements ProcessingUnit {

    private final String zoneId;
    private final SpaceDataGrid space;
    private final CityMap cityMap;
    private final CarAgent carAgent;
    private final TrafficLightController trafficController;
    private final CollisionAvoider avoider;
    private final Map<String, CarState> localCars = new ConcurrentHashMap<>();

    // Deltas collected during the last tick, for frame assembly
    private final List<CarDelta> lastTickDeltas = Collections.synchronizedList(new ArrayList<>());
    private final List<String> lastTickRemoved = Collections.synchronizedList(new ArrayList<>());

    public ZoneProcessingUnit(String zoneId, SpaceDataGrid space, CityMap cityMap,
                               CarAgent carAgent, TrafficLightController trafficController,
                               double minSafeDistance) {
        this.zoneId = zoneId; this.space = space; this.cityMap = cityMap;
        this.carAgent = carAgent; this.trafficController = trafficController;
        this.avoider = new CollisionAvoider(minSafeDistance);
    }

    @Override public String getZoneId()      { return zoneId; }
    @Override public int getLocalCarCount()  { return localCars.size(); }

    @Override
    public void tick(long tickNumber) {
        trafficController.tick(zoneId);
        lastTickDeltas.clear();
        lastTickRemoved.clear();

        List<String> toRemove    = new ArrayList<>();
        List<CarState> toHandOff = new ArrayList<>();

        for (CarState car : new ArrayList<>(localCars.values())) {
            TrafficLightPhase light = resolveLight(car);

            // CarAgent now returns the FULL updated CarState (including segmentOffset, pathIndex, etc.)
            CarState newState = carAgent.advance(car, cityMap, avoider, light, tickNumber);

            if (newState == null || newState.getStatus() == CarStatus.ARRIVED) {
                toRemove.add(car.getCarId());
                lastTickRemoved.add(car.getCarId());
                continue;
            }

            // Build delta for WebSocket broadcast
            lastTickDeltas.add(new CarDelta(
                    newState.getCarId(), newState.getX(), newState.getY(),
                    newState.getHeading(), newState.getStatus().name(), newState.getColor()
            ));

            if (!newState.getCurrentZoneId().equals(zoneId)) {
                // Zone hand-off: write updated state to Hazelcast; ZoneEntryListener adopts it
                toHandOff.add(newState);
            } else {
                localCars.put(car.getCarId(), newState);
                space.putCar(newState);
            }
        }

        toRemove.forEach(id -> { localCars.remove(id); space.removeCar(id); });
        toHandOff.forEach(c -> { localCars.remove(c.getCarId()); space.putCar(c); });
    }

    public List<CarDelta> drainDeltas() {
        List<CarDelta> copy = new ArrayList<>(lastTickDeltas);
        return copy;
    }

    public List<String> drainRemoved() {
        return new ArrayList<>(lastTickRemoved);
    }

    @Override
    public void adoptCar(String carId) {
        CarState car = space.getCar(carId);
        if (car != null && car.getCurrentZoneId().equals(zoneId)) {
            avoider.register(car);
            localCars.put(carId, car);
        }
    }

    @Override
    public void releaseCar(String carId) {
        CarState car = localCars.remove(carId);
        if (car != null) avoider.unregister(car);
    }

    private TrafficLightPhase resolveLight(CarState car) {
        if (car.getCurrentEdgeId() == null) return null;
        Edge edge = cityMap.getEdge(car.getCurrentEdgeId());
        if (edge == null) return null;
        return trafficController.getPhase(edge.sourceNodeId());
    }
}
