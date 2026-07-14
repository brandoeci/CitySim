package edu.escuelaing.citysim.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventStateTest {

    private static EventState base(int requiredActions) {
        Map<String, String> targets = new HashMap<>();
        targets.put("Z_0_0", "E_H_0_0");
        targets.put("Z_0_5", "E_H_0_5");

        return new EventState(
                1L, "ACCIDENT", "ACTIVE", "ALL",
                "Accidente multiple", 90, requiredActions,
                Instant.now(), null,
                new HashMap<>(),
                targets,
                new HashSet<>()
        );
    }

    @Test
    void eventoNuevoNoTieneAcciones() {
        EventState e = base(3);
        assertEquals(0, e.totalActions());
        assertEquals(0, e.progressPercent());
        assertFalse(e.isResolved());
    }

    @Test
    void withActionRegistraElDistrito() {
        EventState e = base(3).withAction("Z_0_0");
        assertEquals(1, e.totalActions());
        assertTrue(e.hasResponded("Z_0_0"));
        assertFalse(e.hasResponded("Z_0_5"));
    }

    @Test
    void unDistritoNoPuedeAportarDosVeces() {
        EventState e = base(3)
                .withAction("Z_0_0")
                .withAction("Z_0_0")
                .withAction("Z_0_0");

        // Aunque se llame tres veces, solo cuenta una: la colaboracion exige
        // administradores distintos, no clicks repetidos.
        assertEquals(1, e.totalActions());
    }

    @Test
    void distritosDistintosSumanCadaUno() {
        EventState e = base(3)
                .withAction("Z_0_0")
                .withAction("Z_0_5");

        assertEquals(2, e.totalActions());
    }

    @Test
    void seResuelveCuandoTodosLosDistritosResponden() {
        EventState e = base(2)
                .withAction("Z_0_0")
                .withAction("Z_0_5");

        assertTrue(e.isResolved());
        assertEquals(100, e.progressPercent());
    }

    @Test
    void noSeResuelveSiFaltaUnDistrito() {
        EventState e = base(2).withAction("Z_0_0");

        assertFalse(e.isResolved());
        assertEquals(50, e.progressPercent());
    }

    @Test
    void progresoSeCalculaSobreLosDistritosRequeridos() {
        EventState e = base(4).withAction("Z_0_0");
        assertEquals(25, e.progressPercent());
    }

    @Test
    void withStatusCambiaElEstadoYConservaLasRespuestas() {
        Instant now = Instant.now();
        EventState e = base(2).withAction("Z_0_0").withStatus("RESOLVED", now);

        assertEquals("RESOLVED", e.status());
        assertEquals(now, e.resolvedAt());
        assertEquals(1, e.totalActions());
        assertTrue(e.hasResponded("Z_0_0"));
    }

    @Test
    void targetEdgeForDevuelveLaViaDelDistrito() {
        EventState e = base(2);
        assertEquals("E_H_0_0", e.targetEdgeFor("Z_0_0"));
        assertEquals("E_H_0_5", e.targetEdgeFor("Z_0_5"));
        assertNull(e.targetEdgeFor("Z_9_9"));
    }

    @Test
    void progresoNuncaSuperaCien() {
        EventState e = base(1)
                .withAction("Z_0_0")
                .withAction("Z_0_5");

        assertEquals(100, e.progressPercent());
        assertTrue(e.isResolved());
    }
}