package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.room.RoomManager;
import edu.escuelaing.citysim.engine.room.RoomSimulation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PresenceController {

    private final SpaceDataGrid space;
    private final DistrictService districtService;
    private final JwtService jwtService;
    private final RoomManager roomManager;

    public PresenceController(SpaceDataGrid space, DistrictService districtService,
                              JwtService jwtService, RoomManager roomManager) {
        this.space = space;
        this.districtService = districtService;
        this.jwtService = jwtService;
        this.roomManager = roomManager;
    }

    @PostMapping("/presence/ping")
    public ResponseEntity<Map<String, Object>> ping(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Token invalido"));
        }

        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;

        mySpace.heartbeat(username);

        return ResponseEntity.ok(buildState(username, mySpace, myDistricts));
    }

    @GetMapping("/zones/districts")
    public ResponseEntity<Map<String, Object>> getDistricts(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        RoomSimulation room = resolveRoom(authHeader);
        SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;
        return ResponseEntity.ok(buildState(username, mySpace, myDistricts));
    }

    @PostMapping("/presence/leave")
    public ResponseEntity<Map<String, Object>> leave(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        if (username != null) {
            RoomSimulation room = resolveRoom(authHeader);
            SpaceDataGrid mySpace = room != null ? room.getSpace() : space;
            mySpace.removePresence(username);
        }
        return ResponseEntity.ok(Map.of("left", true));
    }

    private Map<String, Object> buildState(String username, SpaceDataGrid mySpace, DistrictService myDistricts) {
        List<District> districts = myDistricts.getDistricts();

        District mine = districts.stream()
                .filter(d -> d.username().equals(username))
                .findFirst()
                .orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("districts", districts);
        body.put("myDistrictIndex", mine != null ? mine.index() : -1);
        body.put("activeUsers", districts.size());
        body.put("maxUsers", DistrictService.MAX_USERS);
        return body;
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        return jwtService.extractUsername(token);
    }

    /**
     * Si el token trae roomCode, RESUCITA esa sala si no estaba corriendo
     * (idempotente); si no trae roomCode, modo global de siempre. Antes usaba
     * getRoom() de solo lectura: si el backend se reiniciaba mientras un
     * usuario tenia una sala abierta, su JWT seguia siendo valido pero la
     * sala nunca volvia a levantarse, y esto caia en silencio al modo global.
     */
    private RoomSimulation resolveRoom(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        String roomCode = jwtService.extractRoomCode(token);
        return roomCode != null ? roomManager.startRoom(roomCode) : null;
    }
}