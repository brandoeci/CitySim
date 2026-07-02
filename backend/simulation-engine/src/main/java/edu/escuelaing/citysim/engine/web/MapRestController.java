package edu.escuelaing.citysim.engine.web;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MapRestController {

    private final CityMap cityMap;
    private final SpaceDataGrid space;
    private final ZoneRegistry zoneRegistry;

    public MapRestController(CityMap cityMap, SpaceDataGrid space, ZoneRegistry zoneRegistry) {
        this.cityMap = cityMap; this.space = space; this.zoneRegistry = zoneRegistry;
    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, Object>> getMap() {
        List<double[]> highways = new ArrayList<>();
        List<double[]> streets  = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Edge edge : cityMap.getEdges().values()) {
            String pairKey = edge.sourceNodeId().compareTo(edge.targetNodeId()) < 0
                    ? edge.sourceNodeId() + "|" + edge.targetNodeId()
                    : edge.targetNodeId() + "|" + edge.sourceNodeId();
            if (!seen.add(pairKey)) continue;

            Node src = cityMap.getNode(edge.sourceNodeId());
            Node tgt = cityMap.getNode(edge.targetNodeId());
            if (src == null || tgt == null) continue;

            double[] line = {src.x(), src.y(), tgt.x(), tgt.y()};
            if (edge.isHighway()) highways.add(line); else streets.add(line);
        }

        List<Map<String, Object>> zones = cityMap.getZones().values().stream()
                .map(z -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", z.id());
                    m.put("x", z.minX()); m.put("y", z.minY());
                    m.put("w", z.maxX() - z.minX()); m.put("h", z.maxY() - z.minY());
                    m.put("owner", zoneRegistry.getOwnedZones().containsKey(z.id())
                            ? zoneRegistry.getInstanceId() : "other");
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("width",  cityMap.getWorldWidth());
        result.put("height", cityMap.getWorldHeight());
        result.put("gridWidth",  cityMap.getGridWidth());
        result.put("gridHeight", cityMap.getGridHeight());
        result.put("highways", highways);
        result.put("streets",  streets);
        result.put("zones",    zones);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/cars")
    public ResponseEntity<Collection<CarState>> getAllCars() {
        return ResponseEntity.ok(space.getAllCars().values());
    }

    @GetMapping("/zones")
    public ResponseEntity<List<Map<String, Object>>> getZones() {
        return ResponseEntity.ok(
            zoneRegistry.getOwnedZones().entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("zoneId", e.getKey());
                    m.put("instanceId", zoneRegistry.getInstanceId());
                    m.put("localCars", e.getValue().getLocalCarCount());
                    return m;
                }).collect(Collectors.toList())
        );
    }
}
