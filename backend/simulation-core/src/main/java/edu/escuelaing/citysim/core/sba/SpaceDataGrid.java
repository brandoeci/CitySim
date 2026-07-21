package edu.escuelaing.citysim.core.sba;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.model.SpeedOverride;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Presencia de usuarios (TTL)
    void heartbeat(String username);
    void removePresence(String username);
    boolean isActive(String username);
    int getActiveUserCount();
    List<String> getActiveUsersOrdered();

    // Vias cerradas por los administradores.
    // El A* las evita al calcular rutas, y los carros que iban hacia ellas
    // se re-rutean. Es la herramienta de desvio.
    void blockEdge(String edgeId, String username);
    void unblockEdge(String edgeId);
    boolean isEdgeBlocked(String edgeId);
    Set<String> getBlockedEdges();
    Map<String, String> getBlockedEdgesWithOwner();

    // Multiplicadores de velocidad temporales (REDUCTOR / TURBO).
    void putSpeedOverride(SpeedOverride override);
    SpeedOverride getSpeedOverride(String edgeId);
    Map<String, SpeedOverride> getSpeedOverrides();
    void removeSpeedOverride(String edgeId);
}