package edu.escuelaing.citysim.engine.web;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.car.CarSpawner;
import edu.escuelaing.citysim.engine.car.PopulationMaintainer;
import edu.escuelaing.citysim.engine.room.RoomManager;
import edu.escuelaing.citysim.engine.room.RoomSimulation;
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
    private final RoomManager roomManager;
    private final JwtService jwtService;

    public SimulationRestController(SimulationClock clock, CarSpawner spawner,
                                    SpaceDataGrid space, ZoneRegistry zoneRegistry,
                                    SimulationOrchestrator orchestrator,
                                    PopulationMaintainer population,
                                    RoomManager roomManager, JwtService jwtService) {
        this.clock = clock;
        this.spawner = spawner;
        this.space = space;
        this.zoneRegistry = zoneRegistry;
        this.orchestrator = orchestrator;
        this.population = population;
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestHeader("Authorization") String authHeader) {
        SimulationClock myClock = clockFor(authHeader);
        myClock.start();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "tick", myClock.getTickNumber()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@RequestHeader("Authorization") String authHeader) {
        SimulationClock myClock = clockFor(authHeader);
        myClock.stop();
        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "tick", myClock.getTickNumber()
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
    public ResponseEntity<Map<String, Object>> setPopulation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Integer> body) {
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        PopulationMaintainer myPopulation = room != null ? room.getPopulationMaintainer() : population;
        CarSpawner mySpawner = room != null ? room.getCarSpawner() : spawner;

        Integer target = body.get("target");
        if (target == null) target = body.get("count"); // compatibilidad
        if (target == null) target = 300;

        myPopulation.setTargetCars(target);

        // Arranque inmediato: no esperamos al ciclo del mantenedor.
        long current = mySpace.getCarCount();
        int spawned = 0;
        if (current < target) {
            spawned = mySpawner.spawn((int) Math.min(target - current, 100));
        }

        Map<String, Object> body2 = new LinkedHashMap<>();
        body2.put("target", target);
        body2.put("spawned", spawned);
        body2.put("total", mySpace.getCarCount());
        return ResponseEntity.ok(body2);
    }

    @DeleteMapping("/cars/{carId}")
    public ResponseEntity<Void> removeCar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String carId) {
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        mySpace.removeCar(carId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vacia la ciudad. Pone el objetivo en 0, de lo contrario el mantenedor
     * repondria los carros de inmediato.
     */
    @DeleteMapping("/cars")
    public ResponseEntity<Map<String, Object>> removeAllCars(@RequestHeader("Authorization") String authHeader) {
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        PopulationMaintainer myPopulation = room != null ? room.getPopulationMaintainer() : population;

        myPopulation.setTargetCars(0);
        Map<String, CarState> all = mySpace.getAllCars();
        all.keySet().forEach(mySpace::removeCar);
        return ResponseEntity.ok(Map.of("removed", all.size(), "target", 0));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestHeader("Authorization") String authHeader) {
        RoomSimulation room = resolveRoom(authHeader);
        SimulationClock myClock = room != null ? room.getClock() : clock;
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        ZoneRegistry myZoneRegistry = room != null ? room.getZoneRegistry() : zoneRegistry;
        SimulationOrchestrator myOrchestrator = room != null ? room.getOrchestrator() : orchestrator;
        PopulationMaintainer myPopulation = room != null ? room.getPopulationMaintainer() : population;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("running", myClock.isRunning());
        body.put("tick", myClock.getTickNumber());
        body.put("totalCars", mySpace.getCarCount());
        body.put("targetCars", myPopulation.getTargetCars());
        body.put("fps", myOrchestrator.getCurrentFps());
        body.put("ownedZones", myZoneRegistry.getOwnedZones().size());
        body.put("localCars", myZoneRegistry.getTotalLocalCars());
        body.put("instanceId", myZoneRegistry.getInstanceId());
        return ResponseEntity.ok(body);
    }

    private SimulationClock clockFor(String authHeader) {
        RoomSimulation room = resolveRoom(authHeader);
        return room != null ? room.getClock() : clock;
    }

    /**
     * Si el token trae roomCode, RESUCITA esa sala si no estaba corriendo
     * (idempotente) en vez de solo consultarla -- a diferencia del patron
     * resolveRoom() de solo-lectura que usan los demas controllers. Aqui hace
     * falta: si el usuario ya esta viendo la ciudad de su sala (el frontend
     * asumio que existe porque su JWT trae el roomCode), esta llamada debe
     * garantizar que esa sala este viva, no reportar silenciosamente el
     * estado de la simulacion global.
     */
    private RoomSimulation resolveRoom(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        String roomCode = jwtService.extractRoomCode(token);
        return roomCode != null ? roomManager.startRoom(roomCode) : null;
    }
}
