package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.zone.District;
import edu.escuelaing.citysim.engine.zone.DistrictService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final DistrictService districtService;
    private final JwtService jwtService;

    public EventController(EventService eventService, DistrictService districtService,
                           JwtService jwtService) {
        this.eventService = eventService;
        this.districtService = districtService;
        this.jwtService = jwtService;
    }

    /**
     * Evento activo, con el objetivo concreto de QUIEN pregunta: la via que su
     * distrito debe cerrar, y si ya cumplio.
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActive(@RequestHeader("Authorization") String authHeader) {
        EventState event = eventService.getActiveEvent();
        if (event == null) return ResponseEntity.noContent().build();

        String username = extractUsername(authHeader);
        String headZone = null;
        if (username != null) {
            District d = districtService.getDistrictOf(username);
            if (d != null && !d.zoneIds().isEmpty()) headZone = d.zoneIds().get(0);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("type", event.type());
        body.put("status", event.status());
        body.put("affectedZoneId", event.affectedZoneId());
        body.put("description", event.description());
        body.put("durationSeconds", event.durationSeconds());
        body.put("requiredActions", event.requiredActions());
        body.put("totalActions", event.totalActions());
        body.put("progressPercent", event.progressPercent());
        body.put("startedAt", event.startedAt() != null ? event.startedAt().toString() : null);
        body.put("targetEdges", event.targetEdges());
        body.put("respondedBy", event.respondedBy());

        // Lo que le toca a ESTE administrador
        body.put("myTargetEdge", headZone != null ? event.targetEdgeFor(headZone) : null);
        body.put("iResponded", headZone != null && event.hasResponded(headZone));

        return ResponseEntity.ok(body);
    }

    @GetMapping("/history")
    public ResponseEntity<List<SimulationEvent>> getHistory() {
        return ResponseEntity.ok(eventService.getHistory());
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        return jwtService.extractUsername(token);
    }
}