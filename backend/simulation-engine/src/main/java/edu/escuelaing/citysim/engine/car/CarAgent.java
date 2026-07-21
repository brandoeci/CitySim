package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.model.SpeedOverride;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.pathfinding.PathFinder;
import edu.escuelaing.citysim.core.pathfinding.PathResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CarAgent {

    private static final double LANE_WIDTH = 0.5;
    private static final double STOP_LINE = 0.92;

    private final PathFinder pathFinder;

    public CarAgent(PathFinder pathFinder) {
        this.pathFinder = pathFinder;
    }

    public CarState advance(CarState car, CityMap map, CollisionAvoider avoider,
                            TrafficLightPhase trafficLight, long tick,
                            Set<String> blocked, Map<String, SpeedOverride> speedOverrides) {
        if (car.getStatus() == CarStatus.ARRIVED) return null;
        CarState updated = advanceInternal(car, map, avoider, trafficLight, tick, blocked, speedOverrides);
        avoider.update(car, updated);
        return updated;
    }

    private CarState advanceInternal(CarState car, CityMap map, CollisionAvoider avoider,
                                     TrafficLightPhase trafficLight, long tick,
                                     Set<String> blocked, Map<String, SpeedOverride> speedOverrides) {
        List<String> path = car.getPathNodes();
        int idx = car.getPathIndex();
        if (path == null || idx >= path.size() - 1)
            return car.toBuilder().status(CarStatus.ARRIVED).lastUpdatedTick(tick).build();

        String currentNodeId = path.get(idx);
        String nextNodeId    = path.get(idx + 1);

        Edge edge = findEdge(map, currentNodeId, nextNodeId);
        if (edge == null)
            return car.toBuilder().status(CarStatus.ARRIVED).lastUpdatedTick(tick).build();

        if (!blocked.isEmpty() && blocked.contains(edge.id())) {
            CarState rerouted = reroute(car, map, currentNodeId, blocked, tick);
            if (rerouted != null) return rerouted;
            return car.toBuilder().status(CarStatus.WAITING_TRAFFIC).lastUpdatedTick(tick).build();
        }

        int lane = clampLane(car.getLaneIndex(), edge.laneCount());

        Node src = map.getNode(currentNodeId);
        Node tgt = map.getNode(nextNodeId);
        double heading = Math.atan2(tgt.y() - src.y(), tgt.x() - src.x());

        double advance = edge.speedLimit() / edge.length();
        SpeedOverride override = speedOverrides.get(edge.id());
        if (override != null) advance *= override.factor();
        double newOffset = car.getSegmentOffset() + advance;

        // El semaforo que ve el carro depende del EJE por el que llega: los edges
        // horizontales se llaman E_H_*, los verticales E_V_*. Cuando el eje
        // horizontal tiene verde, el vertical tiene rojo, como en un cruce real.
        boolean horizontal = edge.id().startsWith("E_H_");
        boolean redAhead = (trafficLight != null && trafficLight.isRedFor(horizontal));

        if (redAhead) {
            double target = Math.min(newOffset, STOP_LINE);

            if (avoider.isBlocked(edge.id(), lane, target)) {
                return car.toBuilder()
                        .laneIndex(lane)
                        .status(CarStatus.WAITING_LIGHT)
                        .lastUpdatedTick(tick)
                        .build();
            }

            double cx = src.x() + (tgt.x() - src.x()) * target;
            double cy = src.y() + (tgt.y() - src.y()) * target;
            double[] pStop = laneOffset(heading, lane);

            CarStatus st = (target >= STOP_LINE - 1e-9)
                    ? CarStatus.WAITING_LIGHT : CarStatus.MOVING;

            return car.toBuilder()
                    .x(cx + pStop[0]).y(cy + pStop[1]).heading(heading)
                    .segmentOffset(target)
                    .currentEdgeId(edge.id())
                    .currentZoneId(edge.zoneId())
                    .laneIndex(lane)
                    .status(st)
                    .lastUpdatedTick(tick)
                    .build();
        }

        if (newOffset < 1.0) {
            if (avoider.isBlocked(edge.id(), lane, newOffset))
                return car.toBuilder()
                        .laneIndex(lane)
                        .status(CarStatus.WAITING_TRAFFIC)
                        .lastUpdatedTick(tick)
                        .build();

            double cx = src.x() + (tgt.x() - src.x()) * newOffset;
            double cy = src.y() + (tgt.y() - src.y()) * newOffset;
            double[] p = laneOffset(heading, lane);

            return car.toBuilder()
                    .x(cx + p[0]).y(cy + p[1]).heading(heading)
                    .segmentOffset(newOffset)
                    .currentEdgeId(edge.id())
                    .currentZoneId(edge.zoneId())
                    .laneIndex(lane)
                    .status(CarStatus.MOVING)
                    .lastUpdatedTick(tick)
                    .build();
        } else {
            int nextIdx = idx + 1;
            if (nextIdx >= path.size() - 1) {
                return car.toBuilder()
                        .x(tgt.x()).y(tgt.y()).heading(car.getHeading())
                        .segmentOffset(0.0).pathIndex(nextIdx)
                        .status(CarStatus.ARRIVED).lastUpdatedTick(tick)
                        .build();
            }

            String nextNextId = path.get(nextIdx + 1);
            Edge nextEdge = findEdge(map, nextNodeId, nextNextId);

            if (nextEdge != null && blocked.contains(nextEdge.id())) {
                CarState rerouted = reroute(car, map, nextNodeId, blocked, tick);
                if (rerouted != null) return rerouted;
            }

            String nextZone = (nextEdge != null) ? nextEdge.zoneId() : car.getCurrentZoneId();

            Node nn = map.getNode(nextNextId);
            double newHeading = Math.atan2(nn.y() - tgt.y(), nn.x() - tgt.x());

            int nextLanes = (nextEdge != null) ? nextEdge.laneCount() : 1;
            int newLane = clampLane(lane, nextLanes);
            double[] p = laneOffset(newHeading, newLane);

            return car.toBuilder()
                    .x(tgt.x() + p[0]).y(tgt.y() + p[1]).heading(newHeading)
                    .segmentOffset(0.0)
                    .currentEdgeId(nextEdge != null ? nextEdge.id() : car.getCurrentEdgeId())
                    .currentZoneId(nextZone)
                    .laneIndex(newLane)
                    .status(CarStatus.MOVING).pathIndex(nextIdx).lastUpdatedTick(tick)
                    .build();
        }
    }

    private CarState reroute(CarState car, CityMap map, String fromNodeId,
                             Set<String> blocked, long tick) {
        String dest = car.getDestinationNodeId();
        if (dest == null) return null;

        PathResult alt = pathFinder.findPath(map, fromNodeId, dest, blocked);
        if (!alt.isFound() || alt.nodeIds().size() < 2) return null;

        Node at = map.getNode(fromNodeId);
        if (at == null) return null;

        String nextId = alt.nodeIds().get(1);
        Edge firstEdge = findEdge(map, fromNodeId, nextId);
        if (firstEdge == null) return null;

        Node next = map.getNode(nextId);
        double heading = Math.atan2(next.y() - at.y(), next.x() - at.x());

        int lane = clampLane(car.getLaneIndex(), firstEdge.laneCount());
        double[] p = laneOffset(heading, lane);

        return car.toBuilder()
                .pathNodes(alt.nodeIds())
                .pathIndex(0)
                .segmentOffset(0.0)
                .x(at.x() + p[0]).y(at.y() + p[1])
                .heading(heading)
                .currentEdgeId(firstEdge.id())
                .currentZoneId(firstEdge.zoneId())
                .laneIndex(lane)
                .status(CarStatus.MOVING)
                .lastUpdatedTick(tick)
                .build();
    }

    private double[] laneOffset(double heading, int lane) {
        double dist = (lane + 0.5) * LANE_WIDTH;
        double px = -Math.sin(heading) * dist;
        double py =  Math.cos(heading) * dist;
        return new double[] { px, py };
    }

    private int clampLane(int lane, int laneCount) {
        if (laneCount <= 0) return 0;
        if (lane < 0) return 0;
        return Math.min(lane, laneCount - 1);
    }

    private Edge findEdge(CityMap map, String srcId, String tgtId) {
        for (Edge e : map.getOutgoingEdges(srcId))
            if (e.targetNodeId().equals(tgtId)) return e;
        return null;
    }
}