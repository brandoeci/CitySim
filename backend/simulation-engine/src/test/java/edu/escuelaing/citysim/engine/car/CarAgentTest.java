package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Edge;
import edu.escuelaing.citysim.core.map.MapFactory;
import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.model.CarStatus;
import edu.escuelaing.citysim.core.pathfinding.AStarPathFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CarAgentTest {

    private CityMap cityMap;
    private CarAgent carAgent;
    private CollisionAvoider collisionAvoider;

    /** Sin vias cerradas: el CarAgent nunca re-rutea y se prueba la circulacion normal. */
    private static final Set<String> SIN_BLOQUEOS = Set.of();

    @BeforeEach
    void setUp() {
        cityMap = MapFactory.generate(10, 10, 2, 2);
        carAgent = new CarAgent(new AStarPathFinder());
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

        CarState result = carAgent.advance(arrived, cityMap, collisionAvoider, null, 1L, SIN_BLOQUEOS);
        assertNull(result);
    }

    @Test
    void carroAlFinalDeLaRutaPasaAArrived() {
        CarState atEnd = CarState.builder()
                .carId("car-1")
                .status(CarStatus.MOVING)
                .pathNodes(List.of("N_0_0", "N_0_1"))
                .pathIndex(1)
                .build();

        CarState result = carAgent.advance(atEnd, cityMap, collisionAvoider, null, 5L, SIN_BLOQUEOS);
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

        CarState result = carAgent.advance(noPath, cityMap, collisionAvoider, null, 1L, SIN_BLOQUEOS);
        assertNotNull(result);
        assertEquals(CarStatus.ARRIVED, result.getStatus());
    }

    @Test
    void carroEnMovimientoAvanzaYActualizaTick() {
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

        CarState result = carAgent.advance(moving, cityMap, collisionAvoider, null, 42L, SIN_BLOQUEOS);

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

        CarState result = carAgent.advance(moving, cityMap, collisionAvoider, null, 1L, SIN_BLOQUEOS);
        assertNotNull(result);
    }

    @Test
    void carroEvitaViaCerradaYRecalculaRuta() {
        Edge edge = cityMap.getEdges().values().iterator().next();

        CarState car = CarState.builder()
                .carId("car-1")
                .status(CarStatus.MOVING)
                .currentEdgeId(edge.id())
                .segmentOffset(0.0)
                .laneIndex(0)
                .speed(1.0)
                .pathNodes(List.of(edge.sourceNodeId(), edge.targetNodeId()))
                .pathIndex(0)
                .currentZoneId(edge.zoneId())
                .destinationNodeId(edge.targetNodeId())
                .build();

        // La via por la que iba esta cerrada: no debe seguir usandola.
        Set<String> bloqueadas = Set.of(edge.id());

        CarState result = carAgent.advance(car, cityMap, collisionAvoider, null, 10L, bloqueadas);

        assertNotNull(result);
        assertNotEquals(edge.id(), result.getCurrentEdgeId(),
                "El carro no debe permanecer en la via cerrada");
    }
}