package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.pathfinding.PathFinder;
import edu.escuelaing.citysim.core.pathfinding.PathResult;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CarSpawner {

    private static final Logger log = LoggerFactory.getLogger(CarSpawner.class);

    /** Debe coincidir con LANE_WIDTH de CarAgent. */
    private static final double LANE_WIDTH = 0.5;

    private static final String[] COLORS = {
            "#FF6B35","#F7C59F","#EFEFD0","#004E89","#1A936F",
            "#C6DABF","#88D498","#FFD166","#EF476F","#06D6A0",
            "#118AB2","#073B4C","#E63946","#457B9D","#2DC653",
            "#F4A261","#E76F51","#264653","#2A9D8F","#E9C46A"
    };

    private final SpaceDataGrid space;
    private final CityMap cityMap;
    private final PathFinder pathFinder;
    private final SimulationProperties props;
    private final AtomicInteger colorIndex = new AtomicInteger(0);
    private final List<String> nodeIds;
    private final Random random = new Random();

    public CarSpawner(SpaceDataGrid space, CityMap cityMap, PathFinder pathFinder,
                      SimulationProperties props) {
        this.space = space;
        this.cityMap = cityMap;
        this.pathFinder = pathFinder;
        this.props = props;
        this.nodeIds = new ArrayList<>(cityMap.getNodes().keySet());
        Collections.shuffle(this.nodeIds, random);
    }

    public int spawn(int count) {
        long current = space.getCarCount();
        int toSpawn = (int) Math.min(count, props.getMaxCars() - current);
        if (toSpawn <= 0) { log.warn("Car limit {} reached", props.getMaxCars()); return 0; }
        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) if (spawnOne()) spawned++;
        log.info("Spawned {} cars (total: {})", spawned, space.getCarCount());
        return spawned;
    }

    private boolean spawnOne() {
        String originId = randomNodeId();
        String destId   = randomNodeId();
        for (int i = 0; i < 10 && originId.equals(destId); i++) destId = randomNodeId();

        PathResult path = pathFinder.findPath(cityMap, originId, destId);
        if (!path.isFound()) return false;

        Node origin = cityMap.getNode(originId);
        String carId = UUID.randomUUID().toString();
        String color = COLORS[colorIndex.getAndIncrement() % COLORS.length];

        String firstEdgeId = null;
        String zoneId = origin.zoneId();
        List<String> pathNodes = path.nodeIds();

        // Carriles disponibles en la primera via de la ruta: una avenida ofrece
        // 4, una calle 2. Antes esto estaba fijo en 2 y las avenidas solo usaban
        // la mitad de sus carriles.
        int laneCount = 1;
        double heading = 0.0;

        if (pathNodes.size() >= 2) {
            for (Edge e : cityMap.getOutgoingEdges(pathNodes.get(0))) {
                if (e.targetNodeId().equals(pathNodes.get(1))) {
                    firstEdgeId = e.id();
                    zoneId = e.zoneId();
                    laneCount = Math.max(1, e.laneCount());
                    Node next = cityMap.getNode(pathNodes.get(1));
                    heading = Math.atan2(next.y() - origin.y(), next.x() - origin.x());
                    break;
                }
            }
        }

        int lane = random.nextInt(laneCount);

        // Nace ya sobre su carril, no en el eje de la via.
        double dist = (lane + 0.5) * LANE_WIDTH;
        double px = -Math.sin(heading) * dist;
        double py =  Math.cos(heading) * dist;

        CarState car = CarState.builder()
                .carId(carId)
                .x(origin.x() + px).y(origin.y() + py)
                .heading(heading).speed(1.0)
                .currentZoneId(zoneId)
                .currentEdgeId(firstEdgeId)
                .segmentOffset(0.0)
                .laneIndex(lane)
                .pathNodes(pathNodes).pathIndex(0)
                .status(CarStatus.SPAWNING)
                .lastUpdatedTick(0)
                .color(color)
                .originNodeId(originId)
                .destinationNodeId(destId)
                .build();

        space.putCar(car);
        return true;
    }

    private String randomNodeId() {
        return nodeIds.get(random.nextInt(nodeIds.size()));
    }
}