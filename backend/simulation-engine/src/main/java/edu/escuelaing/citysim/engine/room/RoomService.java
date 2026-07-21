package edu.escuelaing.citysim.engine.room;

import edu.escuelaing.citysim.engine.auth.JwtService;
import edu.escuelaing.citysim.engine.space.HazelcastSpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.DistrictService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * CRUD de salas y membresia. La membresia/capacidad se rastrea con un
 * HazelcastSpaceDataGrid propio por sala (prefijo "room:<code>:"), usando los
 * mismos metodos de presencia (heartbeat/removePresence/getActiveUserCount)
 * que ya usa el modo global -- no se toca la simulacion ni el DistrictService
 * global para nada de esto.
 */
@Service
public class RoomService {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final Random RANDOM = new Random();

    private final RoomRepository roomRepository;
    private final RoomManager roomManager;
    private final JwtService jwtService;

    public RoomService(RoomRepository roomRepository, RoomManager roomManager, JwtService jwtService) {
        this.roomRepository = roomRepository;
        this.roomManager = roomManager;
        this.jwtService = jwtService;
    }

    public Room createRoom(String name, String createdBy) {
        Room room = new Room();
        room.setCode(generateUniqueCode());
        room.setName(name);
        room.setCreatedBy(createdBy);
        room.setMaxPlayers(DistrictService.MAX_USERS);
        room.setStatus(STATUS_WAITING);
        room.setCreatedAt(Instant.now());
        Room saved = roomRepository.save(room);

        // La sala arranca su simulacion al crearse, no al primer join.
        roomManager.startRoom(saved.getCode());

        return saved;
    }

    /**
     * Lista TODAS las salas sin arrancar ninguna: usa el conteo de jugadores
     * solo si la sala ya esta corriendo en este backend. Antes llamaba a
     * summarize() (via gridFor -> startRoom), que arrancaba una simulacion
     * completa por cada sala de la lista con solo consultarla -- con muchas
     * salas acumuladas eso intentaba levantarlas todas de golpe y colgaba el
     * endpoint entero.
     */
    public List<Map<String, Object>> listRooms() {
        return roomRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::summarizeWithoutStarting)
                .collect(Collectors.toList());
    }

    private Map<String, Object> summarizeWithoutStarting(Room room) {
        RoomSimulation running = roomManager.getRoom(room.getCode());
        int players = running != null ? running.getSpace().getActiveUserCount() : 0;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", room.getCode());
        body.put("name", room.getName());
        body.put("players", players);
        body.put("maxPlayers", room.getMaxPlayers());
        body.put("status", room.getStatus());
        return body;
    }

    /** Se une a la sala: chequea cupo, registra presencia, reemite el token con el roomCode. */
    public Map<String, Object> join(String code, String username) {
        Room room = requireRoom(code);
        HazelcastSpaceDataGrid space = gridFor(code);

        if (!space.isActive(username) && space.getActiveUserCount() >= room.getMaxPlayers()) {
            throw new IllegalArgumentException("Sala llena (" + room.getMaxPlayers() + " jugadores)");
        }

        space.heartbeat(username);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", jwtService.generate(username, code));
        body.put("roomCode", code);
        return body;
    }

    public void leave(String code, String username) {
        Room room = requireRoom(code);
        gridFor(room.getCode()).removePresence(username);
    }

    public Map<String, Object> status(String code) {
        Room room = requireRoom(code);
        return summarize(room);
    }

    private Map<String, Object> summarize(Room room) {
        HazelcastSpaceDataGrid space = gridFor(room.getCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", room.getCode());
        body.put("name", room.getName());
        body.put("players", space.getActiveUserCount());
        body.put("maxPlayers", room.getMaxPlayers());
        body.put("status", room.getStatus());
        return body;
    }

    private Room requireRoom(String code) {
        return roomRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("La sala no existe: " + code));
    }

    /**
     * startRoom() es idempotente (devuelve la simulacion ya corriendo si existe),
     * asi que esto tambien resucita la sala si el backend se reinicio.
     */
    private HazelcastSpaceDataGrid gridFor(String code) {
        return roomManager.startRoom(code).getSpace();
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (roomRepository.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
