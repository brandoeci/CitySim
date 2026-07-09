package edu.escuelaing.citysim.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CarStateTest {

    @Test
    void builderConstruyeConTodosLosCampos() {
        CarState car = CarState.builder()
                .carId("car-1")
                .x(10.0).y(20.0)
                .heading(1.5).speed(2.0)
                .currentZoneId("Z_1_1")
                .currentEdgeId("E_1")
                .segmentOffset(0.5)
                .laneIndex(1)
                .pathNodes(List.of("N1", "N2", "N3"))
                .pathIndex(0)
                .status(CarStatus.MOVING)
                .lastUpdatedTick(100)
                .color("#FF0000")
                .originNodeId("N1")
                .destinationNodeId("N3")
                .build();

        assertEquals("car-1", car.getCarId());
        assertEquals(10.0, car.getX());
        assertEquals(20.0, car.getY());
        assertEquals(1.5, car.getHeading());
        assertEquals(2.0, car.getSpeed());
        assertEquals("Z_1_1", car.getCurrentZoneId());
        assertEquals("E_1", car.getCurrentEdgeId());
        assertEquals(0.5, car.getSegmentOffset());
        assertEquals(1, car.getLaneIndex());
        assertEquals(List.of("N1", "N2", "N3"), car.getPathNodes());
        assertEquals(0, car.getPathIndex());
        assertEquals(CarStatus.MOVING, car.getStatus());
        assertEquals(100, car.getLastUpdatedTick());
        assertEquals("#FF0000", car.getColor());
        assertEquals("N1", car.getOriginNodeId());
        assertEquals("N3", car.getDestinationNodeId());
    }

    @Test
    void toBuilderCopiaElEstadoCompleto() {
        CarState original = CarState.builder()
                .carId("car-1").x(5.0).y(5.0)
                .status(CarStatus.MOVING).color("#00FF00")
                .build();

        CarState copy = original.toBuilder().build();

        assertEquals(original.getCarId(), copy.getCarId());
        assertEquals(original.getX(), copy.getX());
        assertEquals(original.getStatus(), copy.getStatus());
        assertEquals(original.getColor(), copy.getColor());
    }

    @Test
    void toBuilderPermiteModificarUnCampoSinTocarLosDemas() {
        CarState original = CarState.builder()
                .carId("car-1").x(5.0).y(5.0)
                .status(CarStatus.MOVING)
                .build();

        CarState modified = original.toBuilder()
                .status(CarStatus.ARRIVED)
                .build();

        // El campo modificado cambio
        assertEquals(CarStatus.ARRIVED, modified.getStatus());
        // Los demas se preservaron
        assertEquals("car-1", modified.getCarId());
        assertEquals(5.0, modified.getX());
        // El original no se toco (inmutabilidad efectiva)
        assertEquals(CarStatus.MOVING, original.getStatus());
    }

    @Test
    void settersModificanElEstado() {
        CarState car = new CarState();
        car.setCarId("car-2");
        car.setX(1.0);
        car.setStatus(CarStatus.SPAWNING);

        assertEquals("car-2", car.getCarId());
        assertEquals(1.0, car.getX());
        assertEquals(CarStatus.SPAWNING, car.getStatus());
    }

    @Test
    void constructorVacioCreaObjetoConValoresPorDefecto() {
        CarState car = new CarState();

        assertNull(car.getCarId());
        assertEquals(0.0, car.getX());
        assertNull(car.getStatus());
    }
}
