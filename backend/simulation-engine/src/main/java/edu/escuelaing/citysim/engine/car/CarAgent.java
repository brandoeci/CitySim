package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CarAgent {

    /** Separacion entre carriles, en unidades de mundo (BLOCK_SIZE es 10.0). */
    private static final double LANE_WIDTH = 0.5;

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

        if (car.getSegmentOffset() < 0.05 && trafficLight != null && trafficLight.isRed())
            return car.toBuilder().status(CarStatus.WAITING_LIGHT).lastUpdatedTick(tick).build();

        // El carril se limita a los que ofrece esta via: una avenida tiene mas
        // carriles que una calle, y el carro se ajusta al entrar.
        int lane = clampLane(car.getLaneIndex(), edge.laneCount());

        double advance   = edge.speedLimit() / edge.length();
        double newOffset = car.getSegmentOffset() + advance;

        if (newOffset < 1.0) {
            if (avoider.isBlocked(edge.id(), lane, newOffset))
                return car.toBuilder()
                        .laneIndex(lane)
                        .status(CarStatus.WAITING_TRAFFIC)
                        .lastUpdatedTick(tick)
                        .build();

            Node src = map.getNode(currentNodeId);
            Node tgt = map.getNode(nextNodeId);

            double heading = Math.atan2(tgt.y() - src.y(), tgt.x() - src.x());

            // Posicion sobre la linea central del tramo
            double cx = src.x() + (tgt.x() - src.x()) * newOffset;
            double cy = src.y() + (tgt.y() - src.y()) * newOffset;

            // Desplazamiento lateral segun el carril
            double[] p = laneOffset(heading, lane, edge.laneCount());

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

            // Al girar, el carro se reubica en un carril valido de la nueva via.
            int nextLanes = (nextEdge != null) ? nextEdge.laneCount() : 1;
            int newLane = clampLane(lane, nextLanes);
            double[] p = laneOffset(heading, newLane, nextLanes);

            return car.toBuilder()
                    .x(tgt.x() + p[0]).y(tgt.y() + p[1]).heading(heading)
                    .segmentOffset(0.0)
                    .currentEdgeId(nextEdge != null ? nextEdge.id() : car.getCurrentEdgeId())
                    .currentZoneId(nextZone)
                    .laneIndex(newLane)
                    .status(CarStatus.MOVING).pathIndex(nextIdx).lastUpdatedTick(tick)
                    .build();
        }
    }

    /**
     * Desplazamiento perpendicular a la direccion de marcha, segun el carril.
     *
     * El vector perpendicular a (cos h, sin h) es (-sin h, cos h). Como el
     * desplazamiento es SIEMPRE al mismo lado relativo a la marcha, los dos
     * sentidos de una misma calle quedan automaticamente en lados opuestos,
     * igual que en una via real.
     */
    private double[] laneOffset(double heading, int lane, int laneCount) {
        if (laneCount <= 0) laneCount = 1;
        // Los carriles se reparten a un lado del eje: 0.5, 1.5, 2.5 ... anchos.
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