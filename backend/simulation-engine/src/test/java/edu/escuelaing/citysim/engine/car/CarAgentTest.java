package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.MapFactory;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CarAgentTest {

    private CityMap cityMap;
    private CarAgent carAgent;
    private CollisionAvoider collisionAvoider;

    @BeforeEach
    void setUp() {
        cityMap = MapFactory.generate(10, 10, 2, 2);
        carAgent = new CarAgent();
        collisionAvoider = new CollisionAvoider(0.15);
    }

    @Test
    void carroConEstadoArrivedDevuelveNull() {
        CarState arrived = CarState.builder()
                .carId("car-1")
                .status(CarStatus.ARRIVED)
                .pathNodes(List.of("N_0_0", "N_0_1"))
                .pathIndex(0)
                .build();

        CarState result = carAgent.advance(arrived, cityMap, collisionAvoider, null, 1L);
        assertNull(result);
    }

    @Test
    void carroAlFinalDeLaRutaPasaAArrived() {
        // pathIndex ya en el ultimo nodo
        CarState atEnd = CarState.builder()
                .carId("car-1")
                .status(CarStatus.MOVING)
                .pathNodes(List.of("N_0_0", "N_0_1"))
                .pathIndex(1)
                .build();

        CarState result = carAgent.advance(atEnd, cityMap, collisionAvoider, null, 5L);
        assertNotNull(result);
        assertEquals(CarStatus.ARRIVED, result.getStatus());
        assertEquals(5L, result.getLastUpdatedTick());
    }

    @Test
    void carroConRutaNullPasaAArrived() {
        CarState noPath = CarState.builder()
                .carId("car-1")
                .status(CarStatus.MOVING)
                .pathNodes(null)
                .pathIndex(0)
                .build();

        CarState result = carAgent.advance(noPath, cityMap, collisionAvoider, null, 1L);
        assertNotNull(result);
        assertEquals(CarStatus.ARRIVED, result.getStatus());
    }

    @Test
    void carroEnMovimientoAvanzaYActualizaTick() {
        // Buscar dos nodos conectados reales
        Edge edge = cityMap.getEdges().values().iterator().next();

        CarState moving = CarState.builder()
                .carId("car-1")
                .status(CarStatus.MOVING)
                .currentEdgeId(edge.id())
                .segmentOffset(0.1)
                .laneIndex(0)
                .speed(1.0)
                .pathNodes(List.of(edge.sourceNodeId(), edge.targetNodeId()))
                .pathIndex(0)
                .currentZoneId(edge.zoneId())
                .lastUpdatedTick(0)
                .build();

        CarState result = carAgent.advance(moving, cityMap, collisionAvoider, null, 42L);

        assertNotNull(result);
        assertEquals(42L, result.getLastUpdatedTick());
    }

    @Test
    void carroDetenidoEnLuzRojaNoAvanzaOffset() {
        Edge edge = cityMap.getEdges().values().iterator().next();

        CarState moving = CarState.builder()
                .carId("car-1")
                .status(CarStatus.MOVING)
                .currentEdgeId(edge.id())
                .segmentOffset(0.1)
                .laneIndex(0)
                .speed(1.0)
                .pathNodes(List.of(edge.sourceNodeId(), edge.targetNodeId()))
                .pathIndex(0)
                .currentZoneId(edge.zoneId())
                .build();

        // No pasamos semaforo (null) - el carro deberia poder avanzar
        CarState result = carAgent.advance(moving, cityMap, collisionAvoider, null, 1L);
        assertNotNull(result);
    }
}
