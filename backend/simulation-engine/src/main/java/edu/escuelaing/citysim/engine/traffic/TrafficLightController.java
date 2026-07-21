package edu.escuelaing.citysim.engine.traffic;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Node;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import edu.escuelaing.citysim.core.model.TrafficLightState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TrafficLightController {

    /** Debe coincidir con HIGHWAY_INTERVAL de MapFactory. */
    private static final int HIGHWAY_INTERVAL = 10;

    private final CityMap cityMap;
    private final Map<String, TrafficLightPhase> localCache = new ConcurrentHashMap<>();

    /** Cruces de avenida con avenida: los unicos con semaforo. */
    private final Set<String> majorIntersections = ConcurrentHashMap.newKeySet();

    public TrafficLightController(CityMap cityMap) {
        this.cityMap = cityMap;
    }

    public void initializeZone(String zoneId) {
        Zone zone = cityMap.getZone(zoneId);
        if (zone == null) return;
        int idx = 0;
        for (String nodeId : zone.nodeIds()) {
            Node node = cityMap.getNode(nodeId);

            // Solo hay semaforo donde se cruzan dos avenidas. Antes se ponia uno
            // en CADA esquina del grid (unas 40.000), y los carros se detenian en
            // todas ellas ante semaforos que ni siquiera se dibujan: por eso el
            // movimiento se veia a tirones.
            if (node == null || !isMajor(nodeId)) continue;

            // Desfase para que no cambien todos a la vez, y turno inicial alterno.
            int offset = (idx * 13) % 68;
            boolean verticalFirst = (idx % 2 == 1);
            idx++;

            TrafficLightPhase phase = TrafficLightPhase.builder()
                    .intersectionId(nodeId)
                    .state(offset < 60 ? TrafficLightState.GREEN : TrafficLightState.YELLOW)
                    .verticalTurn(verticalFirst)
                    .ticksInCurrentState(offset < 60 ? offset : offset - 60)
                    .build();

            localCache.put(nodeId, phase);
            majorIntersections.add(nodeId);
        }
    }

    /**
     * Un cruce es "mayor" cuando se encuentran una avenida horizontal y una
     * vertical. En MapFactory las avenidas van cada HIGHWAY_INTERVAL filas y
     * columnas, asi que el cruce ocurre donde AMBOS indices son multiplos.
     */
    private boolean isMajor(String nodeId) {
        String[] parts = nodeId.split("_");   // N_<row>_<col>
        if (parts.length < 3) return false;
        try {
            int row = Integer.parseInt(parts[1]);
            int col = Integer.parseInt(parts[2]);
            return row % HIGHWAY_INTERVAL == 0 && col % HIGHWAY_INTERVAL == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void tick(String zoneId) {
        Zone zone = cityMap.getZone(zoneId);
        if (zone == null) return;
        for (String nodeId : zone.nodeIds()) {
            TrafficLightPhase phase = localCache.get(nodeId);
            if (phase == null) continue;
            localCache.put(nodeId, phase.advance());
        }
    }

    public TrafficLightPhase getPhase(String intersectionId) {
        return localCache.get(intersectionId);
    }

    /**
     * Fuerza el eje indicado a verde durante durationTicks. Devuelve false si
     * el cruce no existe o no es un cruce mayor (unico tipo con semaforo).
     */
    public boolean forceGreen(String intersectionId, boolean horizontal, int durationTicks) {
        TrafficLightPhase phase = localCache.get(intersectionId);
        if (phase == null || !majorIntersections.contains(intersectionId)) return false;
        localCache.put(intersectionId, phase.forceGreen(horizontal, durationTicks));
        return true;
    }

    public boolean isMajorIntersection(String nodeId) {
        return majorIntersections.contains(nodeId);
    }

    /**
     * Cruces mayores en la misma avenida que intersectionId: misma fila si
     * horizontal (la avenida horizontal que pasa por ahi), misma columna si
     * no. Ordenados por la coordenada que varia, para que OLA VERDE los
     * dispare en el orden en que un carro los recorreria.
     */
    public List<String> intersectionsAlongAvenue(String intersectionId, boolean horizontal) {
        int[] rc = parseRowCol(intersectionId);
        if (rc == null) return List.of();
        int fixed = horizontal ? rc[0] : rc[1];

        List<String> result = new ArrayList<>();
        for (String nodeId : majorIntersections) {
            int[] other = parseRowCol(nodeId);
            if (other != null && (horizontal ? other[0] : other[1]) == fixed) {
                result.add(nodeId);
            }
        }
        result.sort(Comparator.comparingInt(id -> {
            int[] p = parseRowCol(id);
            return horizontal ? p[1] : p[0];
        }));
        return result;
    }

    private int[] parseRowCol(String nodeId) {
        String[] parts = nodeId.split("_");
        if (parts.length < 3) return null;
        try {
            return new int[] { Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Semaforos de los cruces mayores, con el color de CADA eje.
     * El frontend dibuja cuatro luces por cruce: dos para el eje horizontal
     * (este y oeste) y dos para el vertical (norte y sur).
     */
    public List<LightView> getMajorLights() {
        List<LightView> result = new ArrayList<>(majorIntersections.size());
        for (String nodeId : majorIntersections) {
            TrafficLightPhase p = localCache.get(nodeId);
            Node node = cityMap.getNode(nodeId);
            if (p == null || node == null) continue;

            result.add(new LightView(
                    nodeId, node.x(), node.y(),
                    p.stateFor(true).name(),    // eje horizontal
                    p.stateFor(false).name(),   // eje vertical
                    p.isForced()
            ));
        }
        return result;
    }

    /** Un cruce y el color de sus dos ejes. */
    public record LightView(String id, double x, double y,
                            String horizontalState, String verticalState,
                            boolean forced) {}
}