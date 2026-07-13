package edu.escuelaing.citysim.core.sba;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SpaceDataGrid {

    // Cars
    void putCar(CarState car);
    CarState getCar(String carId);
    void removeCar(String carId);
    Collection<CarState> getCarsInZone(String zoneId);
    Map<String, CarState> getAllCars();
    long getCarCount();

    // Traffic lights
    void putTrafficLight(TrafficLightPhase phase);
    TrafficLightPhase getTrafficLight(String intersectionId);

    // Frames
    void publishFrame(SimulationFrame frame);

    // General state
    void putSimulationState(String key, String value);
    String getSimulationState(String key);

    // Eventos colaborativos
    void putActiveEvent(EventState event);
    EventState getActiveEvent();
    void clearActiveEvent();

    // Asignaciones de zona
    void assignZone(String username, String zoneId);
    String getAssignedZone(String username);
    Map<String, String> getAllZoneAssignments();

    // Presencia de usuarios (TTL): un usuario sigue "activo" mientras
    // renueve su heartbeat. Si deja de hacerlo, su entrada expira sola.
    void heartbeat(String username);
    void removePresence(String username);
    boolean isActive(String username);
    int getActiveUserCount();

    /**
     * Usuarios activos ordenados por antiguedad (el primero en conectarse va primero).
     * El orden es determinista para que el reparto de distritos sea estable.
     */
    List<String> getActiveUsersOrdered();
}