package edu.escuelaing.citysim.core.sba;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;

import java.util.Collection;
import java.util.Map;

public interface SpaceDataGrid {
    void putCar(CarState car);
    CarState getCar(String carId);
    void removeCar(String carId);
    Collection<CarState> getCarsInZone(String zoneId);
    Map<String, CarState> getAllCars();
    long getCarCount();

    void putTrafficLight(TrafficLightPhase phase);
    TrafficLightPhase getTrafficLight(String intersectionId);

    void publishFrame(SimulationFrame frame);

    void putSimulationState(String key, String value);
    String getSimulationState(String key);
}
