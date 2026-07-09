package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.zone.ZoneAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final ZoneAssignmentService zoneAssignment;
    private final JwtService jwtService;

    public EventController(EventService eventService, ZoneAssignmentService zoneAssignment,
                           JwtService jwtService) {
        this.eventService = eventService;
        this.zoneAssignment = zoneAssignment;
        this.jwtService = jwtService;
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActive() {
        EventState event = eventService.getActiveEvent();
        if (event == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(event);
    }

    @PostMapping("/{eventId}/respond")
    public ResponseEntity<?> respond(@PathVariable Long eventId,
                                     @RequestHeader("Authorization") String authHeader,
                                     @RequestBody Map<String, String> body) {
        String token = authHeader.replace("Bearer ", "").trim();
        String username = jwtService.extractUsername(token);
        String zone = zoneAssignment.getZone(username);
        if (zone == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Sin zona asignada"));

        String actionStr = body.get("action");
        if (actionStr == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Falta el campo 'action'"));

        EventAction action;
        try {
            action = EventAction.valueOf(actionStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Accion desconocida: " + actionStr));
        }

        try {
            eventService.registerAction(eventId, zone, action);
            return ResponseEntity.ok(Map.of("registered", true, "zone", zone, "action", action.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<SimulationEvent>> getHistory() {
        return ResponseEntity.ok(eventService.getHistory());
    }
}
