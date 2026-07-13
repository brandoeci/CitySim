package edu.escuelaing.citysim.engine.web;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.car.CarSpawner;
import edu.escuelaing.citysim.engine.car.PopulationMaintainer;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import edu.escuelaing.citysim.engine.simulation.SimulationOrchestrator;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationRestController {

    private final SimulationClock clock;
    private final CarSpawner spawner;
    private final SpaceDataGrid space;
    private final ZoneRegistry zoneRegistry;
    private final SimulationOrchestrator orchestrator;
    private final PopulationMaintainer population;

    public SimulationRestController(SimulationClock clock, CarSpawner spawner,
                                    SpaceDataGrid space, ZoneRegistry zoneRegistry,
                                    SimulationOrchestrator orchestrator,
                                    PopulationMaintainer population) {
        this.clock = clock;
        this.spawner = spawner;
        this.space = space;
        this.zoneRegistry = zoneRegistry;
        this.orchestrator = orchestrator;
        this.population = population;
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

    /**
     * Ajusta la poblacion objetivo de la ciudad.
     *
     * No agrega carros sueltos: fija el numero que el PopulationMaintainer se
     * encarga de sostener. Si los carros llegan a su destino y desaparecen, el
     * mantenedor repone hasta este numero. Asi el trafico se mantiene estable
     * sin intervencion manual.
     */
    @PostMapping("/cars")
    public ResponseEntity<Map<String, Object>> setPopulation(@RequestBody Map<String, Integer> body) {
        Integer target = body.get("target");
        if (target == null) target = body.get("count"); // compatibilidad
        if (target == null) target = 300;

        population.setTargetCars(target);

        // Arranque inmediato: no esperamos al ciclo del mantenedor.
        long current = space.getCarCount();
        int spawned = 0;
        if (current < target) {
            spawned = spawner.spawn((int) Math.min(target - current, 100));
        }

        Map<String, Object> body2 = new LinkedHashMap<>();
        body2.put("target", target);
        body2.put("spawned", spawned);
        body2.put("total", space.getCarCount());
        return ResponseEntity.ok(body2);
    }

    @DeleteMapping("/cars/{carId}")
    public ResponseEntity<Void> removeCar(@PathVariable String carId) {
        space.removeCar(carId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vacia la ciudad. Pone el objetivo en 0, de lo contrario el mantenedor
     * repondria los carros de inmediato.
     */
    @DeleteMapping("/cars")
    public ResponseEntity<Map<String, Object>> removeAllCars() {
        population.setTargetCars(0);
        Map<String, CarState> all = space.getAllCars();
        all.keySet().forEach(space::removeCar);
        return ResponseEntity.ok(Map.of("removed", all.size(), "target", 0));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("running", clock.isRunning());
        body.put("tick", clock.getTickNumber());
        body.put("totalCars", space.getCarCount());
        body.put("targetCars", population.getTargetCars());
        body.put("fps", orchestrator.getCurrentFps());
        body.put("ownedZones", zoneRegistry.getOwnedZones().size());
        body.put("localCars", zoneRegistry.getTotalLocalCars());
        body.put("instanceId", zoneRegistry.getInstanceId());
        return ResponseEntity.ok(body);
    }
}