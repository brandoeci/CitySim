package edu.escuelaing.citysim.engine.traffic;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.MapFactory;
import edu.escuelaing.citysim.core.model.TrafficLightPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficLightControllerTest {

    private CityMap cityMap;
    private TrafficLightController controller;

    @BeforeEach
    void setUp() {
        // Mapa real 10x10 con 4 zonas
        cityMap = MapFactory.generate(10, 10, 2, 2);
        controller = new TrafficLightController(cityMap);
    }

    @Test
    void initializeZoneCreaSemaforosEnIntersecciones() {
        // Tomar una zona real del mapa
        String zoneId = cityMap.getZones().keySet().iterator().next();
        controller.initializeZone(zoneId);

        // Al menos un nodo con 2+ entradas debe tener semaforo
        boolean hayAlgunSemaforo = cityMap.getZone(zoneId).nodeIds().stream()
                .anyMatch(nodeId -> controller.getPhase(nodeId) != null);
        assertTrue(hayAlgunSemaforo);
    }

    @Test
    void initializeZoneConZonaInexistenteNoFalla() {
        assertDoesNotThrow(() -> controller.initializeZone("ZONA_QUE_NO_EXISTE"));
    }

    @Test
    void getPhaseDevuelveNullParaNodoSinSemaforo() {
        assertNull(controller.getPhase("NODO_INEXISTENTE"));
    }

    @Test
    void tickAvanzaLasFasesDeLosSemaforos() {
        String zoneId = cityMap.getZones().keySet().iterator().next();
        controller.initializeZone(zoneId);

        // Encontrar un nodo con semaforo
        String nodeConSemaforo = cityMap.getZone(zoneId).nodeIds().stream()
                .filter(nodeId -> controller.getPhase(nodeId) != null)
                .findFirst().orElse(null);

        if (nodeConSemaforo != null) {
            TrafficLightPhase antes = controller.getPhase(nodeConSemaforo);
            int ticksAntes = antes.getTicksInCurrentState();

            controller.tick(zoneId);

            TrafficLightPhase despues = controller.getPhase(nodeConSemaforo);
            // El tick cambio algo: o incremento ticks o cambio de estado
            boolean avanzo = despues.getTicksInCurrentState() != ticksAntes
                    || despues.getState() != antes.getState();
            assertTrue(avanzo);
        }
    }

    @Test
    void tickConZonaInexistenteNoFalla() {
        assertDoesNotThrow(() -> controller.tick("ZONA_QUE_NO_EXISTE"));
    }

    @Test
    void tickSinInicializarNoFalla() {
        String zoneId = cityMap.getZones().keySet().iterator().next();
        // No llamamos initializeZone, los semaforos no existen
        assertDoesNotThrow(() -> controller.tick(zoneId));
    }
}
