package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.model.CarDelta;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.model.EventObjective;
import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.core.model.ObjectiveKind;
import edu.escuelaing.citysim.core.model.SpeedOverride;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.sba.ProcessingUnit;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.car.CarAgent;
import edu.escuelaing.citysim.engine.car.CollisionAvoider;
import edu.escuelaing.citysim.engine.event.EventObjectiveTracker;
import edu.escuelaing.citysim.engine.event.EventType;
import edu.escuelaing.citysim.engine.event.ObjectiveZoneSnapshot;
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
    private final EventObjectiveTracker eventTracker;
    private final Map<String, CarState> localCars = new ConcurrentHashMap<>();

    private final List<CarDelta> lastTickDeltas = Collections.synchronizedList(new ArrayList<>());
    private final List<String> lastTickRemoved = Collections.synchronizedList(new ArrayList<>());

    // Estado local para SHIELD_AREA: total acumulado de entradas por objetivo,
    // desde que empezo el evento que se esta siguiendo actualmente.
    private Long trackedEventId = null;
    private final Map<String, Integer> localAreaEntryCumulative = new HashMap<>();

    public ZoneProcessingUnit(String zoneId, SpaceDataGrid space, CityMap cityMap,
                              CarAgent carAgent, TrafficLightController trafficController,
                              double minSafeDistance, EventObjectiveTracker eventTracker) {
        this.zoneId = zoneId;
        this.space = space;
        this.cityMap = cityMap;
        this.carAgent = carAgent;
        this.trafficController = trafficController;
        this.avoider = new CollisionAvoider(minSafeDistance);
        this.eventTracker = eventTracker;
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

        // Igual criterio para los speed-overrides (REDUCTOR/TURBO): se leen y
        // se limpian los expirados una vez por tick, comparando contra el
        // tickNumber real -- nada de scheduler aparte para expirarlos.
        Map<String, SpeedOverride> speedOverrides = currentSpeedOverrides(tickNumber);

        // Igual criterio para los objetivos del evento activo: se leen una vez
        // por tick, no por carro.
        EventState activeEvent = space.getActiveEvent();
        List<EventObjective> trackedObjectives = relevantObjectives(activeEvent);

        Map<String, Integer> areaEntryCumulative = null;
        Map<String, Boolean> corridorDirty = null;
        Map<String, Integer> junctionWaiting = null;
        Map<String, Integer> areaInside = null;

        if (!trackedObjectives.isEmpty()) {
            areaEntryCumulative = new HashMap<>();
            corridorDirty = new HashMap<>();
            junctionWaiting = new HashMap<>();
            areaInside = new HashMap<>();
            for (EventObjective obj : trackedObjectives) {
                switch (obj.kind()) {
                    case SHIELD_AREA -> areaEntryCumulative.put(obj.zoneId(),
                            localAreaEntryCumulative.getOrDefault(obj.zoneId(), 0));
                    case CLEAR_CORRIDOR -> corridorDirty.put(obj.zoneId(), false);
                    case RELIEVE_JUNCTION -> junctionWaiting.put(obj.zoneId(), 0);
                    case EVACUATE_AREA -> areaInside.put(obj.zoneId(), 0);
                    case CLOSE_EDGE -> { /* se resuelve al cerrar la via, no aqui */ }
                }
            }
        }

        List<String> toRemove = new ArrayList<>();
        List<CarState> toHandOff = new ArrayList<>();

        for (CarState car : new ArrayList<>(localCars.values())) {
            TrafficLightPhase light = resolveLight(car);

            CarState newState = carAgent.advance(car, cityMap, avoider, light, tickNumber, blocked, speedOverrides);

            if (newState == null || newState.getStatus() == CarStatus.ARRIVED) {
                toRemove.add(car.getCarId());
                lastTickRemoved.add(car.getCarId());
                continue;
            }

            lastTickDeltas.add(new CarDelta(
                    newState.getCarId(), newState.getX(), newState.getY(),
                    newState.getHeading(), newState.getStatus().name(), newState.getColor()
            ));

            if (!trackedObjectives.isEmpty()) {
                observeForObjectives(car, newState, trackedObjectives,
                        areaEntryCumulative, corridorDirty, junctionWaiting, areaInside);
            }

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

        if (!trackedObjectives.isEmpty()) {
            eventTracker.reportZone(zoneId, new ObjectiveZoneSnapshot(
                    areaEntryCumulative, corridorDirty, junctionWaiting, areaInside));
        }
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
        String nextNodeId = nextNodeIdOf(car);
        if (nextNodeId == null) return null;
        return trafficController.getPhase(nextNodeId);
    }

    private String nextNodeIdOf(CarState state) {
        List<String> path = state.getPathNodes();
        int idx = state.getPathIndex();
        if (path == null || idx >= path.size() - 1) return null;
        return path.get(idx + 1);
    }

    /** Overrides vigentes; los que ya expiraron se limpian del Space aqui mismo. */
    private Map<String, SpeedOverride> currentSpeedOverrides(long tickNumber) {
        Map<String, SpeedOverride> all = space.getSpeedOverrides();
        if (all.isEmpty()) return all;

        Map<String, SpeedOverride> current = new HashMap<>();
        for (SpeedOverride override : all.values()) {
            if (override.expiresAtTick() <= tickNumber) {
                space.removeSpeedOverride(override.edgeId());
            } else {
                current.put(override.edgeId(), override);
            }
        }
        return current;
    }

    /** Objetivos del evento activo que esta zona debe vigilar este tick. */
    private List<EventObjective> relevantObjectives(EventState activeEvent) {
        if (activeEvent == null || !"ACTIVE".equals(activeEvent.status())) {
            trackedEventId = null;
            localAreaEntryCumulative.clear();
            return List.of();
        }

        if (!activeEvent.eventId().equals(trackedEventId)) {
            trackedEventId = activeEvent.eventId();
            localAreaEntryCumulative.clear();
        }

        ObjectiveKind kind = EventType.valueOf(activeEvent.type()).getObjectiveKind();
        if (kind == ObjectiveKind.CLOSE_EDGE) return List.of();

        return new ArrayList<>(activeEvent.objectives().values());
    }

    private void observeForObjectives(CarState car, CarState newState, List<EventObjective> objectives,
                                      Map<String, Integer> areaEntryCumulative,
                                      Map<String, Boolean> corridorDirty,
                                      Map<String, Integer> junctionWaiting,
                                      Map<String, Integer> areaInside) {
        for (EventObjective obj : objectives) {
            switch (obj.kind()) {
                case SHIELD_AREA -> {
                    boolean wasInside = pointInRect(car.getX(), car.getY(), obj);
                    boolean isInside = pointInRect(newState.getX(), newState.getY(), obj);
                    if (!wasInside && isInside) {
                        int updated = localAreaEntryCumulative.merge(obj.zoneId(), 1, Integer::sum);
                        areaEntryCumulative.put(obj.zoneId(), updated);
                    }
                }
                case CLEAR_CORRIDOR -> {
                    String edgeId = stripReverse(newState.getCurrentEdgeId());
                    if (edgeId != null && obj.edgeIds().contains(edgeId)) {
                        corridorDirty.put(obj.zoneId(), true);
                    }
                }
                case RELIEVE_JUNCTION -> {
                    boolean waiting = newState.getStatus() == CarStatus.WAITING_LIGHT
                            || newState.getStatus() == CarStatus.WAITING_TRAFFIC;
                    if (waiting && obj.intersectionId() != null
                            && obj.intersectionId().equals(nextNodeIdOf(newState))) {
                        junctionWaiting.merge(obj.zoneId(), 1, Integer::sum);
                    }
                }
                case EVACUATE_AREA -> {
                    if (pointInRect(newState.getX(), newState.getY(), obj)) {
                        areaInside.merge(obj.zoneId(), 1, Integer::sum);
                    }
                }
                case CLOSE_EDGE -> { /* no aplica */ }
            }
        }
    }

    private boolean pointInRect(double x, double y, EventObjective obj) {
        return obj.minX() != null
                && x >= obj.minX() && x <= obj.maxX()
                && y >= obj.minY() && y <= obj.maxY();
    }

    private String stripReverse(String edgeId) {
        if (edgeId == null) return null;
        return edgeId.endsWith("_R") ? edgeId.substring(0, edgeId.length() - 2) : edgeId;
    }
}
