package edu.escuelaing.citysim.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventStateTest {

    private static EventObjective closeEdgeObjective(String zoneId, String edgeId) {
        return new EventObjective(zoneId, ObjectiveKind.CLOSE_EDGE, List.of(edgeId),
                null, null, null, null, null, 1, 0, false, false);
    }

    private static EventState base(int requiredActions) {
        Map<String, EventObjective> objectives = new HashMap<>();
        objectives.put("Z_0_0", closeEdgeObjective("Z_0_0", "E_H_0_0"));
        objectives.put("Z_0_5", closeEdgeObjective("Z_0_5", "E_H_0_5"));

        return new EventState(
                1L, "ACCIDENT", "ACTIVE", "ALL",
                "Accidente multiple", 90, requiredActions,
                Instant.now(), null,
                objectives
        );
    }

    private static EventState respond(EventState e, String zoneId) {
        EventObjective updated = e.objectiveFor(zoneId).withCurrent(1).withCompleted();
        return e.withObjective(zoneId, updated);
    }

    @Test
    void eventoNuevoNoTieneAcciones() {
        EventState e = base(3);
        assertEquals(0, e.totalActions());
        assertEquals(0, e.progressPercent());
        assertFalse(e.isResolved());
    }

    @Test
    void withObjectiveRegistraElDistrito() {
        EventState e = respond(base(3), "Z_0_0");
        assertEquals(1, e.totalActions());
        assertTrue(e.hasResponded("Z_0_0"));
        assertFalse(e.hasResponded("Z_0_5"));
    }

    @Test
    void unDistritoNoPuedeAportarDosVeces() {
        EventState e = base(3);
        e = respond(e, "Z_0_0");
        e = respond(e, "Z_0_0");
        e = respond(e, "Z_0_0");

        // Aunque se llame tres veces, solo cuenta una: la colaboracion exige
        // administradores distintos, no clicks repetidos.
        assertEquals(1, e.totalActions());
    }

    @Test
    void distritosDistintosSumanCadaUno() {
        EventState e = base(3);
        e = respond(e, "Z_0_0");
        e = respond(e, "Z_0_5");

        assertEquals(2, e.totalActions());
    }

    @Test
    void seResuelveCuandoTodosLosDistritosResponden() {
        EventState e = base(2);
        e = respond(e, "Z_0_0");
        e = respond(e, "Z_0_5");

        assertTrue(e.isResolved());
        assertEquals(100, e.progressPercent());
    }

    @Test
    void noSeResuelveSiFaltaUnDistrito() {
        EventState e = respond(base(2), "Z_0_0");

        assertFalse(e.isResolved());
        assertEquals(50, e.progressPercent());
    }

    @Test
    void progresoSeCalculaSobreLosDistritosRequeridos() {
        EventState e = respond(base(4), "Z_0_0");
        assertEquals(25, e.progressPercent());
    }

    @Test
    void withStatusCambiaElEstadoYConservaLasRespuestas() {
        Instant now = Instant.now();
        EventState e = respond(base(2), "Z_0_0").withStatus("RESOLVED", now);

        assertEquals("RESOLVED", e.status());
        assertEquals(now, e.resolvedAt());
        assertEquals(1, e.totalActions());
        assertTrue(e.hasResponded("Z_0_0"));
    }

    @Test
    void objectiveForDevuelveElObjetivoDelDistrito() {
        EventState e = base(2);
        assertEquals("E_H_0_0", e.objectiveFor("Z_0_0").targetEdge());
        assertEquals("E_H_0_5", e.objectiveFor("Z_0_5").targetEdge());
        assertNull(e.objectiveFor("Z_9_9"));
    }

    @Test
    void progresoNuncaSuperaCien() {
        EventState e = base(1);
        e = respond(e, "Z_0_0");
        e = respond(e, "Z_0_5");

        assertEquals(100, e.progressPercent());
        assertTrue(e.isResolved());
    }

    @Test
    void isFailedEsTrueSiAlgunObjetivoFallo() {
        EventState e = base(2);
        assertFalse(e.isFailed());

        EventObjective shield = new EventObjective("Z_0_0", ObjectiveKind.SHIELD_AREA, List.of(),
                0.0, 0.0, 30.0, 30.0, null, 25, 0, false, false).withFailed();
        e = e.withObjective("Z_0_0", shield);

        assertTrue(e.isFailed());
    }
}
