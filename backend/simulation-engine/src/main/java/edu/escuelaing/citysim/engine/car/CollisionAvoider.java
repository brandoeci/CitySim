package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.model.CarState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks car positions per road edge to prevent overlapping.
 * One instance per ZoneProcessingUnit — not shared across zones.
 */
public class CollisionAvoider {

    private final double minSafeDistance;

    // edgeId → laneIndex → sorted list of (segmentOffset, carId)
    private final Map<String, Map<Integer, TreeMap<Double, String>>> segmentOccupancy =
            new ConcurrentHashMap<>();

    public CollisionAvoider(double minSafeDistance) {
        this.minSafeDistance = minSafeDistance;
    }

    public void register(CarState car) {
        if (car.getCurrentEdgeId() == null) return;
        getLane(car.getCurrentEdgeId(), car.getLaneIndex())
                .put(car.getSegmentOffset(), car.getCarId());
    }

    public void unregister(CarState car) {
        if (car.getCurrentEdgeId() == null) return;
        TreeMap<Double, String> lane = getLane(car.getCurrentEdgeId(), car.getLaneIndex());
        lane.remove(car.getSegmentOffset());
    }

    public void update(CarState oldState, CarState newState) {
        if (oldState.getCurrentEdgeId() != null &&
            oldState.getCurrentEdgeId().equals(newState.getCurrentEdgeId()) &&
            oldState.getLaneIndex() == newState.getLaneIndex()) {
            TreeMap<Double, String> lane = getLane(oldState.getCurrentEdgeId(), oldState.getLaneIndex());
            lane.remove(oldState.getSegmentOffset());
            lane.put(newState.getSegmentOffset(), newState.getCarId());
        } else {
            unregister(oldState);
            register(newState);
        }
    }

    public boolean isBlocked(String edgeId, int laneIndex, double newOffset) {
        TreeMap<Double, String> lane = getLane(edgeId, laneIndex);
        Map.Entry<Double, String> ahead = lane.higherEntry(newOffset);
        if (ahead == null) return false;
        return (ahead.getKey() - newOffset) < minSafeDistance;
    }

    public void clear() {
        segmentOccupancy.clear();
    }

    private TreeMap<Double, String> getLane(String edgeId, int laneIndex) {
        return segmentOccupancy
                .computeIfAbsent(edgeId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(laneIndex, k -> new TreeMap<>());
    }
}
