package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stateless service that advances a single car by one tick.
 * Returns the full updated CarState so all internal fields (segmentOffset, pathIndex, etc.)
 * are properly persisted by the caller.
 */
@Component
public class CarAgent {

    /** Advance the car one tick. Returns the full updated CarState, or null if ARRIVED. */
    public CarState advance(CarState car, CityMap map, CollisionAvoider avoider,
                            TrafficLightPhase trafficLight, long tick) {
        if (car.getStatus() == CarStatus.ARRIVED) return null;
        CarState updated = advanceInternal(car, map, avoider, trafficLight, tick);
        avoider.update(car, updated);
        return updated;
    }

    private CarState advanceInternal(CarState car, CityMap map, CollisionAvoider avoider,
                                     TrafficLightPhase trafficLight, long tick) {
        List<String> path = car.getPathNodes();
        int idx = car.getPathIndex();

        if (path == null || idx >= path.size() - 1)
            return car.toBuilder().status(CarStatus.ARRIVED).lastUpdatedTick(tick).build();

        String currentNodeId = path.get(idx);
        String nextNodeId    = path.get(idx + 1);
        Edge edge = findEdge(map, currentNodeId, nextNodeId);
        if (edge == null)
            return car.toBuilder().status(CarStatus.ARRIVED).lastUpdatedTick(tick).build();

        // Red light: stop at the beginning of the segment
        if (car.getSegmentOffset() < 0.05 && trafficLight != null && trafficLight.isRed())
            return car.toBuilder().status(CarStatus.WAITING_LIGHT).lastUpdatedTick(tick).build();

        double advance   = edge.speedLimit() / edge.length();
        double newOffset = car.getSegmentOffset() + advance;

        if (newOffset < 1.0) {
            if (avoider.isBlocked(edge.id(), car.getLaneIndex(), newOffset))
                return car.toBuilder().status(CarStatus.WAITING_TRAFFIC).lastUpdatedTick(tick).build();

            Node src = map.getNode(currentNodeId);
            Node tgt = map.getNode(nextNodeId);
            double x       = src.x() + (tgt.x() - src.x()) * newOffset;
            double y       = src.y() + (tgt.y() - src.y()) * newOffset;
            double heading = Math.atan2(tgt.y() - src.y(), tgt.x() - src.x());

            return car.toBuilder()
                    .x(x).y(y).heading(heading)
                    .segmentOffset(newOffset)
                    .currentEdgeId(edge.id())
                    .currentZoneId(edge.zoneId())
                    .status(CarStatus.MOVING)
                    .lastUpdatedTick(tick)
                    .build();
        } else {
            // Move to next node in path
            int nextIdx = idx + 1;
            if (nextIdx >= path.size() - 1) {
                Node tgt = map.getNode(nextNodeId);
                return car.toBuilder()
                        .x(tgt.x()).y(tgt.y()).heading(car.getHeading())
                        .segmentOffset(0.0).pathIndex(nextIdx)
                        .status(CarStatus.ARRIVED).lastUpdatedTick(tick)
                        .build();
            }
            String nextNextId = path.get(nextIdx + 1);
            Edge nextEdge = findEdge(map, nextNodeId, nextNextId);
            String nextZone = (nextEdge != null) ? nextEdge.zoneId() : car.getCurrentZoneId();

            Node tgt = map.getNode(nextNodeId);
            Node nn  = map.getNode(nextNextId);
            double heading = Math.atan2(nn.y() - tgt.y(), nn.x() - tgt.x());

            return car.toBuilder()
                    .x(tgt.x()).y(tgt.y()).heading(heading)
                    .segmentOffset(0.0)
                    .currentEdgeId(nextEdge != null ? nextEdge.id() : car.getCurrentEdgeId())
                    .currentZoneId(nextZone)
                    .status(CarStatus.MOVING).pathIndex(nextIdx).lastUpdatedTick(tick)
                    .build();
        }
    }

    private Edge findEdge(CityMap map, String srcId, String tgtId) {
        for (Edge e : map.getOutgoingEdges(srcId))
            if (e.targetNodeId().equals(tgtId)) return e;
        return null;
    }
}
