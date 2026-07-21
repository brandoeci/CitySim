package edu.escuelaing.citysim.engine.room;

import edu.escuelaing.citysim.engine.auth.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final JwtService jwtService;

    public RoomController(RoomService roomService, JwtService jwtService) {
        this.roomService = roomService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("Authorization") String authHeader,
                                     @RequestBody Map<String, String> body) {
        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");

        String name = body.get("name");
        if (name == null || name.isBlank()) return error("Falta el campo 'name'");

        Room room = roomService.createRoom(name, username);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", room.getCode());
        response.put("name", room.getName());
        response.put("status", room.getStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(roomService.listRooms());
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> join(@PathVariable String code,
                                   @RequestHeader("Authorization") String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");

        try {
            return ResponseEntity.ok(roomService.join(code, username));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<?> leave(@PathVariable String code,
                                    @RequestHeader("Authorization") String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");

        try {
            roomService.leave(code, username);
            return ResponseEntity.ok(Map.of("left", true));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @GetMapping("/{code}/status")
    public ResponseEntity<?> status(@PathVariable String code) {
        try {
            return ResponseEntity.ok(roomService.status(code));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        return jwtService.extractUsername(token);
    }
}
