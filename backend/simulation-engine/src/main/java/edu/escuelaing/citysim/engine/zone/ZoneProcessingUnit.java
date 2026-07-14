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

    private final List<CarDelta> lastTickDeltas = Collections.synchronizedList(new ArrayList<>());
    private final List<String> lastTickRemoved = Collections.synchronizedList(new ArrayList<>());

    public ZoneProcessingUnit(String zoneId, SpaceDataGrid space, CityMap cityMap,
                              CarAgent carAgent, TrafficLightController trafficController,
                              double minSafeDistance) {
        this.zoneId = zoneId;
        this.space = space;
        this.cityMap = cityMap;
        this.carAgent = carAgent;
        this.trafficController = trafficController;
        this.avoider = new CollisionAvoider(minSafeDistance);
    }

    @Override
    public String getZoneId() {
        return zoneId;
    }

    @Override
    public int getLocalCarCount() {
        return localCars.size();
    }

    @Override
    public void tick(long tickNumber) {
        trafficController.tick(zoneId);
        lastTickDeltas.clear();
        lastTickRemoved.clear();

        // Las vias cerradas se leen UNA vez por tick, no una vez por carro.
        // Asi todos los carros del tick ven exactamente el mismo estado del
        // Space, y se evitan miles de lecturas al cluster por segundo.
        Set<String> blocked = space.getBlockedEdges();

        List<String> toRemove = new ArrayList<>();
        List<CarState> toHandOff = new ArrayList<>();

        for (CarState car : new ArrayList<>(localCars.values())) {
            TrafficLightPhase light = resolveLight(car);

            CarState newState = carAgent.advance(car, cityMap, avoider, light, tickNumber, blocked);

            if (newState == null || newState.getStatus() == CarStatus.ARRIVED) {
                toRemove.add(car.getCarId());
                lastTickRemoved.add(car.getCarId());
                continue;
            }

            lastTickDeltas.add(new CarDelta(
                    newState.getCarId(), newState.getX(), newState.getY(),
                    newState.getHeading(), newState.getStatus().name(), newState.getColor()
            ));

            if (!newState.getCurrentZoneId().equals(zoneId)) {
                toHandOff.add(newState);
            } else {
                localCars.put(car.getCarId(), newState);
                space.putCar(newState);
            }
        }

        toRemove.forEach(id -> {
            localCars.remove(id);
            space.removeCar(id);
        });
        toHandOff.forEach(c -> {
            localCars.remove(c.getCarId());
            space.putCar(c);
        });
    }

    public List<CarDelta> drainDeltas() {
        return new ArrayList<>(lastTickDeltas);
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

    /**
     * Semaforo de la interseccion a la que LLEGA el carro (el nodo destino de
     * su tramo), no del que salio. Es el que determina si debe parar antes del
     * cruce.
     */
    private TrafficLightPhase resolveLight(CarState car) {
        List<String> path = car.getPathNodes();
        int idx = car.getPathIndex();
        if (path == null || idx >= path.size() - 1) return null;
        String nextNodeId = path.get(idx + 1);
        return trafficController.getPhase(nextNodeId);
    }
}