package edu.escuelaing.citysim.engine.traffic;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.model.TrafficLightState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages traffic light state machines entirely in local memory.
 * Each backend instance owns specific zones, so no cross-instance sharing needed.
 */
@Component
public class TrafficLightController {

    private final CityMap cityMap;
    private final Map<String, TrafficLightPhase> localCache = new ConcurrentHashMap<>();

    public TrafficLightController(CityMap cityMap) {
        this.cityMap = cityMap;
    }

    public void initializeZone(String zoneId) {
        Zone zone = cityMap.getZone(zoneId);
        if (zone == null) return;
        int idx = 0;
        for (String nodeId : zone.nodeIds()) {
            Node node = cityMap.getNode(nodeId);
            if (node == null || node.incomingEdgeIds().size() < 2) continue;
            int offset = (idx++ * 7) % 85;
            TrafficLightPhase phase = TrafficLightPhase.builder()
                    .intersectionId(nodeId)
                    .state(offset < 40 ? TrafficLightState.GREEN :
                           offset < 45 ? TrafficLightState.YELLOW : TrafficLightState.RED)
                    .ticksInCurrentState(offset % 40)
                    .build();
            localCache.put(nodeId, phase);
        }
    }

    public void tick(String zoneId) {
        Zone zone = cityMap.getZone(zoneId);
        if (zone == null) return;
        for (String nodeId : zone.nodeIds()) {
            TrafficLightPhase phase = localCache.get(nodeId);
            if (phase == null) continue;
            localCache.put(nodeId, phase.advance());
        }
    }

    public TrafficLightPhase getPhase(String intersectionId) {
        return localCache.get(intersectionId);
    }
}
