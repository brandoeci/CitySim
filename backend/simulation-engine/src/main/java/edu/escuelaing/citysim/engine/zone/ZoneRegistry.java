package edu.escuelaing.citysim.engine.zone;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.car.CarAgent;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.space.HazelcastSpaceDataGrid;
import edu.escuelaing.citysim.engine.traffic.TrafficLightController;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributes city zones across backend instances using Hazelcast IMap.putIfAbsent.
 * Each instance claims zones by instance ID; unclaimed zones are picked up on startup.
 */
@Component
public class ZoneRegistry {

    private static final Logger log = LoggerFactory.getLogger(ZoneRegistry.class);

    private final HazelcastInstance hazelcast;
    private final SpaceDataGrid space;
    private final CityMap cityMap;
    private final CarAgent carAgent;
    private final TrafficLightController trafficController;
    private final SimulationProperties props;

    private final String instanceId = UUID.randomUUID().toString();
    private final Map<String, ZoneProcessingUnit> ownedZones = new ConcurrentHashMap<>();

    public ZoneRegistry(HazelcastInstance hazelcast, SpaceDataGrid space, CityMap cityMap,
                        CarAgent carAgent, TrafficLightController trafficController,
                        SimulationProperties props) {
        this.hazelcast = hazelcast;
        this.space = space;
        this.cityMap = cityMap;
        this.carAgent = carAgent;
        this.trafficController = trafficController;
        this.props = props;
    }

    @PostConstruct
    public void initialize() {
        IMap<String, String> ownershipMap = hazelcast.getMap("zone-ownership");

        List<String> allZoneIds = new ArrayList<>(cityMap.getZones().keySet());
        Collections.sort(allZoneIds);

        // First pass: try to claim unclaimed zones
        for (String zoneId : allZoneIds) {
            String existingOwner = ownershipMap.putIfAbsent(zoneId, instanceId);
            if (existingOwner == null) {
                claimZone(zoneId);
            }
        }

        // If we claimed nothing, all zones are held by previous (dead) instances — take over
        if (ownedZones.isEmpty()) {
            log.warn("No unclaimed zones found; clearing stale ownership and re-claiming");
            ownershipMap.clear();
            space.getAllCars().keySet().forEach(space::removeCar); // clear stale car data
            for (String zoneId : allZoneIds) {
                ownershipMap.put(zoneId, instanceId);
                claimZone(zoneId);
            }
        }

        // Register Hazelcast entry listener for car hand-offs
        if (space instanceof HazelcastSpaceDataGrid hzSpace) {
            hzSpace.getCarMap().addEntryListener(new ZoneEntryListener(ownedZones), true);
        }

        log.info("Instance {} owns {}/{} zones", instanceId, ownedZones.size(), allZoneIds.size());
    }

    public Map<String, ZoneProcessingUnit> getOwnedZones() {
        return Collections.unmodifiableMap(ownedZones);
    }

    public String getInstanceId() {
        return instanceId;
    }

    public int getTotalLocalCars() {
        return ownedZones.values().stream().mapToInt(ZoneProcessingUnit::getLocalCarCount).sum();
    }

    private void claimZone(String zoneId) {
        ZoneProcessingUnit zpu = new ZoneProcessingUnit(
                zoneId, space, cityMap, carAgent, trafficController,
                props.getMinSafeDistance()
        );
        ownedZones.put(zoneId, zpu);
        trafficController.initializeZone(zoneId);
    }
}
