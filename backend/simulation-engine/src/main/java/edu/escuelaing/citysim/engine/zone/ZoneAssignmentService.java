package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ZoneAssignmentService {

    private final ZoneRegistry zoneRegistry;
    private final SpaceDataGrid space;

    public ZoneAssignmentService(ZoneRegistry zoneRegistry, SpaceDataGrid space) {
        this.zoneRegistry = zoneRegistry;
        this.space = space;
    }

    public String assignZone(String username) {
        String existing = space.getAssignedZone(username);
        if (existing != null) return existing;

        Map<String, String> all = space.getAllZoneAssignments();
        Map<String, Long> counts = new java.util.concurrent.ConcurrentHashMap<>();
        zoneRegistry.getOwnedZones().keySet().forEach(z -> counts.put(z, 0L));
        all.values().forEach(z -> counts.merge(z, 1L, Long::sum));

        String assigned = counts.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(zoneRegistry.getOwnedZones().keySet().iterator().next());

        space.assignZone(username, assigned);
        return assigned;
    }

    public String getZone(String username) {
        return space.getAssignedZone(username);
    }

    public Map<String, String> getAllAssignments() {
        return space.getAllZoneAssignments();
    }
}
