package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventState;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.room.RoomManager;
import edu.escuelaing.citysim.engine.room.RoomSimulation;
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
    private final RoomManager roomManager;

    public EventController(EventService eventService, DistrictService districtService,
                           JwtService jwtService, RoomManager roomManager) {
        this.eventService = eventService;
        this.districtService = districtService;
        this.jwtService = jwtService;
        this.roomManager = roomManager;
    }

    /**
     * Evento activo, con el objetivo concreto de QUIEN pregunta: la via que su
     * distrito debe cerrar, y si ya cumplio.
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActive(@RequestHeader("Authorization") String authHeader) {
        RoomSimulation room = resolveRoom(authHeader);
        EventService myEvents = room != null ? room.getEventService() : eventService;
        DistrictService myDistricts = room != null ? room.getDistrictService() : districtService;

        EventState event = myEvents.getActiveEvent();
        if (event == null) return ResponseEntity.noContent().build();

        String username = extractUsername(authHeader);
        String headZone = null;
        if (username != null) {
            District d = myDistricts.getDistrictOf(username);
            if (d != null) headZone = d.headZone();
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
        body.put("objectives", event.objectives());

        // Lo que le toca a ESTE administrador
        body.put("myObjective", headZone != null ? event.objectiveFor(headZone) : null);
        body.put("iCompleted", headZone != null && event.hasResponded(headZone));

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