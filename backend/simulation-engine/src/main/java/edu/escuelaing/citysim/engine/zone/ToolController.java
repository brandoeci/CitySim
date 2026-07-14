package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.event.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final SpaceDataGrid space;
    private final CityMap cityMap;
    private final DistrictService districtService;
    private final JwtService jwtService;
    private final EventService eventService;

    public ToolController(SpaceDataGrid space, CityMap cityMap,
                          DistrictService districtService, JwtService jwtService,
                          EventService eventService) {
        this.space = space;
        this.cityMap = cityMap;
        this.districtService = districtService;
        this.jwtService = jwtService;
        this.eventService = eventService;
    }

    @PostMapping("/close-edge")
    public ResponseEntity<Map<String, Object>> closeEdge(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");

        String edgeId = body.get("edgeId");
        if (edgeId == null) return error("Falta el campo 'edgeId'");

        Edge edge = cityMap.getEdge(edgeId);
        if (edge == null) return error("La via no existe: " + edgeId);

        District mine = districtService.getDistrictOf(username);
        if (mine == null) return error("No administras ningun distrito");

        if (!mine.zoneIds().contains(edge.zoneId()))
            return error("Esa via no esta en tu distrito");

        space.blockEdge(edgeId, username);
        String reverseId = reverseOf(edgeId);
        if (cityMap.getEdge(reverseId) != null) {
            space.blockEdge(reverseId, username);
        }

        // Si esta era la via objetivo del evento activo, el aporte se registra
        // solo: la accion en el mapa ES la respuesta al evento. No hay boton.
        boolean contributed = false;
        String headZone = mine.zoneIds().isEmpty() ? null : mine.zoneIds().get(0);
        if (headZone != null) {
            contributed = eventService.tryRegisterByEdge(headZone, edgeId);
        }

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("closed", true);
        ok.put("edgeId", edgeId);
        ok.put("by", username);
        ok.put("eventContribution", contributed);
        return ResponseEntity.ok(ok);
    }

    @PostMapping("/open-edge")
    public ResponseEntity<Map<String, Object>> openEdge(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        String username = extractUsername(authHeader);
        if (username == null) return error("Token invalido");

        String edgeId = body.get("edgeId");
        if (edgeId == null) return error("Falta el campo 'edgeId'");

        String owner = space.getBlockedEdgesWithOwner().get(edgeId);
        if (owner == null) return error("Esa via no esta cerrada");

        if (!owner.equals(username))
            return error("Esa via la cerro " + owner);

        space.unblockEdge(edgeId);
        space.unblockEdge(reverseOf(edgeId));

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("opened", true);
        ok.put("edgeId", edgeId);
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/blocked-edges")
    public ResponseEntity<Map<String, Object>> blockedEdges() {
        Map<String, String> blocked = space.getBlockedEdgesWithOwner();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("blocked", blocked);
        body.put("count", blocked.size());
        return ResponseEntity.ok(body);
    }

    private String reverseOf(String edgeId) {
        if (edgeId.endsWith("_R")) return edgeId.substring(0, edgeId.length() - 2);
        return edgeId + "_R";
    }

    private ResponseEntity<Map<String, Object>> error(String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", msg);
        return ResponseEntity.badRequest().body(body);
    }

    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        if (!jwtService.isValid(token)) return null;
        return jwtService.extractUsername(token);
    }
}