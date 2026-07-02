package edu.escuelaing.citysim.engine.web;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.car.CarSpawner;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import edu.escuelaing.citysim.engine.simulation.SimulationOrchestrator;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationRestController {

    private final SimulationClock clock;
    private final CarSpawner spawner;
    private final SpaceDataGrid space;
    private final ZoneRegistry zoneRegistry;
    private final SimulationOrchestrator orchestrator;

    public SimulationRestController(SimulationClock clock, CarSpawner spawner,
                                     SpaceDataGrid space, ZoneRegistry zoneRegistry,
                                     SimulationOrchestrator orchestrator) {
        this.clock = clock;
        this.spawner = spawner;
        this.space = space;
        this.zoneRegistry = zoneRegistry;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        clock.start();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "tick", clock.getTickNumber()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        clock.stop();
        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "tick", clock.getTickNumber()
        ));
    }

    @PostMapping("/cars")
    public ResponseEntity<Map<String, Object>> spawnCars(@RequestBody Map<String, Integer> body) {
        int count = body.getOrDefault("count", 10);
        int spawned = spawner.spawn(count);
        return ResponseEntity.ok(Map.of(
                "spawned", spawned,
                "total", space.getCarCount()
        ));
    }

    @DeleteMapping("/cars/{carId}")
    public ResponseEntity<Void> removeCar(@PathVariable String carId) {
        space.removeCar(carId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cars")
    public ResponseEntity<Map<String, Object>> removeAllCars() {
        Map<String, CarState> all = space.getAllCars();
        all.keySet().forEach(space::removeCar);
        return ResponseEntity.ok(Map.of("removed", all.size()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "running", clock.isRunning(),
                "tick", clock.getTickNumber(),
                "totalCars", space.getCarCount(),
                "fps", orchestrator.getCurrentFps(),
                "ownedZones", zoneRegistry.getOwnedZones().size(),
                "localCars", zoneRegistry.getTotalLocalCars(),
                "instanceId", zoneRegistry.getInstanceId()
        ));
    }
}
