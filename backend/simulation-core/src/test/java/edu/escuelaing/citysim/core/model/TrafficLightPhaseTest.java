package edu.escuelaing.citysim.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficLightPhaseTest {

    @Test
    void builderConstruyeConValoresPorDefecto() {
        TrafficLightPhase phase = TrafficLightPhase.builder()
                .intersectionId("N1")
                .build();

        assertEquals("N1", phase.getIntersectionId());
        assertEquals(TrafficLightState.RED, phase.getState());
        assertEquals(40, phase.getGreenDuration());
        assertEquals(5, phase.getYellowDuration());
        assertEquals(40, phase.getRedDuration());
    }

    @Test
    void isGreenSoloEsTrueEnVerde() {
        TrafficLightPhase green = TrafficLightPhase.builder()
                .state(TrafficLightState.GREEN).build();
        assertTrue(green.isGreen());
        assertFalse(green.isRed());
    }

    @Test
    void isRedEsTrueEnRojoYAmarillo() {
        TrafficLightPhase red = TrafficLightPhase.builder()
                .state(TrafficLightState.RED).build();
        TrafficLightPhase yellow = TrafficLightPhase.builder()
                .state(TrafficLightState.YELLOW).build();

        assertTrue(red.isRed());
        assertTrue(yellow.isRed());   // amarillo cuenta como rojo para detener
        assertFalse(red.isGreen());
    }

    @Test
    void advanceIncrementaTicksSinCambiarEstadoSiNoLlegaAlLimite() {
        TrafficLightPhase phase = TrafficLightPhase.builder()
                .state(TrafficLightState.GREEN)
                .greenDuration(40)
                .ticksInCurrentState(10)
                .build();

        TrafficLightPhase next = phase.advance();

        assertEquals(TrafficLightState.GREEN, next.getState());
        assertEquals(11, next.getTicksInCurrentState());
    }

    @Test
    void advanceTransicionaDeVerdeAAmarillo() {
        TrafficLightPhase phase = TrafficLightPhase.builder()
                .state(TrafficLightState.GREEN)
                .greenDuration(40)
                .ticksInCurrentState(39)
                .build();

        TrafficLightPhase next = phase.advance();

        assertEquals(TrafficLightState.YELLOW, next.getState());
        assertEquals(0, next.getTicksInCurrentState());
    }

    @Test
    void advanceTransicionaDeAmarilloARojo() {
        TrafficLightPhase phase = TrafficLightPhase.builder()
                .state(TrafficLightState.YELLOW)
                .yellowDuration(5)
                .ticksInCurrentState(4)
                .build();

        TrafficLightPhase next = phase.advance();

        assertEquals(TrafficLightState.RED, next.getState());
        assertEquals(0, next.getTicksInCurrentState());
    }

    @Test
    void advanceTransicionaDeRojoAVerde() {
        TrafficLightPhase phase = TrafficLightPhase.builder()
                .state(TrafficLightState.RED)
                .redDuration(40)
                .ticksInCurrentState(39)
                .build();

        TrafficLightPhase next = phase.advance();

        assertEquals(TrafficLightState.GREEN, next.getState());
        assertEquals(0, next.getTicksInCurrentState());
    }

    @Test
    void advanceCompletaUnCicloCompleto() {
        // GREEN -> YELLOW -> RED -> GREEN
        TrafficLightPhase phase = TrafficLightPhase.builder()
                .state(TrafficLightState.GREEN)
                .greenDuration(1).yellowDuration(1).redDuration(1)
                .ticksInCurrentState(0)
                .build();

        TrafficLightPhase afterGreen = phase.advance();
        assertEquals(TrafficLightState.YELLOW, afterGreen.getState());

        TrafficLightPhase afterYellow = afterGreen.advance();
        assertEquals(TrafficLightState.RED, afterYellow.getState());

        TrafficLightPhase afterRed = afterYellow.advance();
        assertEquals(TrafficLightState.GREEN, afterRed.getState());
    }

    @Test
    void toBuilderPreservaLosCampos() {
        TrafficLightPhase original = TrafficLightPhase.builder()
                .intersectionId("N5")
                .state(TrafficLightState.GREEN)
                .greenDuration(30)
                .build();

        TrafficLightPhase copy = original.toBuilder().build();

        assertEquals("N5", copy.getIntersectionId());
        assertEquals(TrafficLightState.GREEN, copy.getState());
        assertEquals(30, copy.getGreenDuration());
    }
}
