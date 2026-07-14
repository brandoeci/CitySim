package edu.escuelaing.citysim.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrafficLightPhaseTest {

    private static TrafficLightPhase phase(TrafficLightState state, boolean verticalTurn, int ticks) {
        return TrafficLightPhase.builder()
                .intersectionId("N_10_10")
                .state(state)
                .verticalTurn(verticalTurn)
                .ticksInCurrentState(ticks)
                .greenDuration(60)
                .yellowDuration(8)
                .build();
    }

    @Test
    void ejesOpuestos_cuandoHorizontalTieneVerdeVerticalTieneRojo() {
        TrafficLightPhase p = phase(TrafficLightState.GREEN, false, 0);

        assertEquals(TrafficLightState.GREEN, p.stateFor(true));   // horizontal
        assertEquals(TrafficLightState.RED,   p.stateFor(false));  // vertical
    }

    @Test
    void ejesOpuestos_cuandoEsTurnoVerticalElHorizontalEstaEnRojo() {
        TrafficLightPhase p = phase(TrafficLightState.GREEN, true, 0);

        assertEquals(TrafficLightState.RED,   p.stateFor(true));
        assertEquals(TrafficLightState.GREEN, p.stateFor(false));
    }

    @Test
    void amarilloDetieneAlCarro() {
        TrafficLightPhase p = phase(TrafficLightState.YELLOW, false, 0);

        assertTrue(p.isRedFor(true), "El amarillo obliga a detenerse");
        assertTrue(p.isRedFor(false), "El otro eje sigue en rojo");
    }

    @Test
    void verdePermitePasar() {
        TrafficLightPhase p = phase(TrafficLightState.GREEN, false, 0);

        assertFalse(p.isRedFor(true));
        assertTrue(p.isRedFor(false));
    }

    @Test
    void verdePasaAAmarilloAlAgotarSuDuracion() {
        TrafficLightPhase p = phase(TrafficLightState.GREEN, false, 59).advance();

        assertEquals(TrafficLightState.YELLOW, p.getState());
        assertEquals(0, p.getTicksInCurrentState());
    }

    @Test
    void verdeNoCambiaAntesDeTiempo() {
        TrafficLightPhase p = phase(TrafficLightState.GREEN, false, 10).advance();

        assertEquals(TrafficLightState.GREEN, p.getState());
        assertEquals(11, p.getTicksInCurrentState());
    }

    @Test
    void alTerminarElAmarilloElPasoCambiaDeEje() {
        TrafficLightPhase p = phase(TrafficLightState.YELLOW, false, 7).advance();

        assertEquals(TrafficLightState.GREEN, p.getState());
        assertTrue(p.isVerticalTurn(), "El turno pasa al eje vertical");
        assertEquals(0, p.getTicksInCurrentState());
    }

    @Test
    void cicloCompletoDevuelveElPasoAlEjeOriginal() {
        TrafficLightPhase p = phase(TrafficLightState.YELLOW, false, 7).advance();  // -> vertical
        assertTrue(p.isVerticalTurn());

        p = phase(TrafficLightState.YELLOW, true, 7).advance();                     // -> horizontal
        assertFalse(p.isVerticalTurn());
    }

    @Test
    void elCicloConservaLaInterseccion() {
        TrafficLightPhase p = phase(TrafficLightState.GREEN, false, 59).advance();
        assertEquals("N_10_10", p.getIntersectionId());
    }
}