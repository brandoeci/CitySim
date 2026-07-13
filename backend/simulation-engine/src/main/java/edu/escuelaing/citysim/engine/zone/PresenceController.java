package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.auth.JwtService;
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

    public PresenceController(SpaceDataGrid space, DistrictService districtService,
                              JwtService jwtService) {
        this.space = space;
        this.districtService = districtService;
        this.jwtService = jwtService;
    }

    @PostMapping("/presence/ping")
    public ResponseEntity<Map<String, Object>> ping(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Token invalido"));
        }

        space.heartbeat(username);

        return ResponseEntity.ok(buildState(username));
    }

    @GetMapping("/zones/districts")
    public ResponseEntity<Map<String, Object>> getDistricts(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        return ResponseEntity.ok(buildState(username));
    }

    @PostMapping("/presence/leave")
    public ResponseEntity<Map<String, Object>> leave(
            @RequestHeader("Authorization") String authHeader) {

        String username = extractUsername(authHeader);
        if (username != null) {
            space.removePresence(username);
        }
        return ResponseEntity.ok(Map.of("left", true));
    }

    private Map<String, Object> buildState(String username) {
        List<District> districts = districtService.getDistricts();

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
}