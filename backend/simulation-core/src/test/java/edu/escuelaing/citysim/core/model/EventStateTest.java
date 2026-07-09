package edu.escuelaing.citysim.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventStateTest {

    private EventState baseEvent() {
        return new EventState(
                1L, "ACCIDENT", "ACTIVE", "Z_1_1",
                "Accidente reportado", 60, 5,
                Instant.now(), null, new HashMap<>()
        );
    }

    @Test
    void withActionIncrementaLaZonaCorrecta() {
        EventState event = baseEvent();
        EventState updated = event.withAction("Z_1_1");

        assertEquals(1, updated.actionsByZone().get("Z_1_1"));
        assertEquals(1, updated.totalActions());
    }

    @Test
    void withActionNoMutaElOriginal() {
        EventState event = baseEvent();
        event.withAction("Z_1_1");

        // El original sigue vacio (inmutabilidad)
        assertEquals(0, event.totalActions());
    }

    @Test
    void withActionAcumulaVariasAcciones() {
        EventState event = baseEvent()
                .withAction("Z_1_1")
                .withAction("Z_1_1")
                .withAction("Z_2_2");

        assertEquals(2, event.actionsByZone().get("Z_1_1"));
        assertEquals(1, event.actionsByZone().get("Z_2_2"));
        assertEquals(3, event.totalActions());
    }

    @Test
    void withStatusCambiaEstadoSinMutarOriginal() {
        EventState event = baseEvent();
        Instant resolvedAt = Instant.now();
        EventState resolved = event.withStatus("RESOLVED", resolvedAt);

        assertEquals("RESOLVED", resolved.status());
        assertEquals(resolvedAt, resolved.resolvedAt());
        assertEquals("ACTIVE", event.status()); // original intacto
    }

    @Test
    void totalActionsSumaTodasLasZonas() {
        Map<String, Integer> actions = new HashMap<>();
        actions.put("Z_1_1", 3);
        actions.put("Z_2_2", 2);
        EventState event = new EventState(1L, "ACCIDENT", "ACTIVE", "Z_1_1",
                "desc", 60, 5, Instant.now(), null, actions);

        assertEquals(5, event.totalActions());
    }

    @Test
    void progressPercentCalculaPorcentaje() {
        EventState event = baseEvent()
                .withAction("Z_1_1")
                .withAction("Z_1_1"); // 2 de 5 requeridas

        assertEquals(40, event.progressPercent()); // 2/5 = 40%
    }

    @Test
    void progressPercentTopaEn100() {
        EventState event = baseEvent();
        for (int i = 0; i < 10; i++) event = event.withAction("Z_1_1"); // 10 de 5

        assertEquals(100, event.progressPercent());
    }

    @Test
    void progressPercentEsCeroSiNoHayAccionesRequeridas() {
        EventState event = new EventState(1L, "ACCIDENT", "ACTIVE", "Z_1_1",
                "desc", 60, 0, Instant.now(), null, new HashMap<>());

        assertEquals(0, event.progressPercent());
    }

    @Test
    void isResolvedEsTrueAlAlcanzarAccionesRequeridas() {
        EventState event = baseEvent();
        for (int i = 0; i < 5; i++) event = event.withAction("Z_1_1");

        assertTrue(event.isResolved());
    }

    @Test
    void isResolvedEsFalseSiFaltanAcciones() {
        EventState event = baseEvent()
                .withAction("Z_1_1")
                .withAction("Z_1_1"); // solo 2 de 5

        assertFalse(event.isResolved());
    }
}
