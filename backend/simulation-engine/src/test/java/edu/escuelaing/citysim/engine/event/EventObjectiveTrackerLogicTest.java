package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.EventObjective;
import edu.escuelaing.citysim.core.model.ObjectiveKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba la matematica de victoria/derrota de cada mecanica nueva de forma
 * aislada (sin Hazelcast ni Spring), llamando directamente los metodos
 * estaticos de {@link EventObjectiveTracker}. Cada mecanica tiene su propio
 * metodo de test para poder atribuir una falla a una sola mecanica.
 */
class EventObjectiveTrackerLogicTest {

    private static final String ZONE = "Z_0_0";

    private static EventObjective shieldObjective(int threshold, int current) {
        return new EventObjective(ZONE, ObjectiveKind.SHIELD_AREA, List.of(),
                0.0, 0.0, 30.0, 30.0, null, threshold, current, false, false);
    }

    private static EventObjective corridorObjective(int threshold, int current) {
        return new EventObjective(ZONE, ObjectiveKind.CLEAR_CORRIDOR,
                List.of("E_H_10_0", "E_H_10_1"), null, null, null, null, null,
                threshold, current, false, false);
    }

    private static EventObjective evacuationObjective(int threshold, int current) {
        return new EventObjective(ZONE, ObjectiveKind.EVACUATE_AREA, List.of(),
                0.0, 0.0, 30.0, 30.0, null, threshold, current, false, false);
    }

    private static EventObjective junctionObjective(int threshold, int current) {
        return new EventObjective(ZONE, ObjectiveKind.RELIEVE_JUNCTION, List.of(),
                null, null, null, null, "N_10_10", threshold, current, false, false);
    }

    private static ObjectiveZoneSnapshot snapshot(Map<String, Integer> areaEntryCumulative,
                                                   Map<String, Boolean> corridorDirty,
                                                   Map<String, Integer> junctionWaiting,
                                                   Map<String, Integer> areaInside) {
        return new ObjectiveZoneSnapshot(areaEntryCumulative, corridorDirty, junctionWaiting, areaInside);
    }

    // --- AREA_SHIELD -------------------------------------------------

    @Test
    void shieldToleranciaBajaConCadaEntrada() {
        EventObjective obj = shieldObjective(25, 25);
        var snapshots = List.of(snapshot(Map.of(ZONE, 3), Map.of(), Map.of(), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeShield(obj, snapshots);

        assertEquals(22, result.current());
        assertFalse(result.failed());
    }

    @Test
    void shieldFallaAlLlegarACero() {
        EventObjective obj = shieldObjective(25, 25);
        var snapshots = List.of(snapshot(Map.of(ZONE, 25), Map.of(), Map.of(), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeShield(obj, snapshots);

        assertEquals(0, result.current());
        assertTrue(result.failed());
    }

    @Test
    void shieldSumaLasEntradasDeVariasZonas() {
        EventObjective obj = shieldObjective(25, 25);
        var snapshots = List.of(
                snapshot(Map.of(ZONE, 10), Map.of(), Map.of(), Map.of()),
                snapshot(Map.of(ZONE, 5), Map.of(), Map.of(), Map.of())
        );

        EventObjective result = EventObjectiveTracker.recomputeShield(obj, snapshots);

        assertEquals(10, result.current());
        assertFalse(result.failed());
    }

    // --- VIP_CONVOY (CLEAR_CORRIDOR) ----------------------------------

    @Test
    void corridorSubeCadaCicloLimpio() {
        EventObjective obj = corridorObjective(15, 5);
        var snapshots = List.of(snapshot(Map.of(), Map.of(ZONE, false), Map.of(), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeCorridor(obj, snapshots);

        assertEquals(6, result.current());
        assertFalse(result.completed());
    }

    @Test
    void corridorSeReiniciaSiHayUnCarro() {
        EventObjective obj = corridorObjective(15, 10);
        var snapshots = List.of(snapshot(Map.of(), Map.of(ZONE, true), Map.of(), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeCorridor(obj, snapshots);

        assertEquals(0, result.current());
        assertFalse(result.completed());
    }

    @Test
    void corridorSeCompletaAlLlegarAlUmbral() {
        EventObjective obj = corridorObjective(15, 14);
        var snapshots = List.of(snapshot(Map.of(), Map.of(ZONE, false), Map.of(), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeCorridor(obj, snapshots);

        assertEquals(15, result.current());
        assertTrue(result.completed());
    }

    // --- EVACUATION (EVACUATE_AREA) -----------------------------------

    @Test
    void evacuacionSumaLosVehiculosDeCadaZona() {
        EventObjective obj = evacuationObjective(5, 20);
        var snapshots = List.of(
                snapshot(Map.of(), Map.of(), Map.of(), Map.of(ZONE, 6)),
                snapshot(Map.of(), Map.of(), Map.of(), Map.of(ZONE, 2))
        );

        EventObjective result = EventObjectiveTracker.recomputeEvacuation(obj, snapshots);

        assertEquals(8, result.current());
        assertFalse(result.completed());
    }

    @Test
    void evacuacionSeCompletaBajoElUmbral() {
        EventObjective obj = evacuationObjective(5, 20);
        var snapshots = List.of(snapshot(Map.of(), Map.of(), Map.of(), Map.of(ZONE, 3)));

        EventObjective result = EventObjectiveTracker.recomputeEvacuation(obj, snapshots);

        assertEquals(3, result.current());
        assertTrue(result.completed());
    }

    // --- GRIDLOCK (RELIEVE_JUNCTION) ----------------------------------

    @Test
    void gridlockSumaLaColaDeCadaZona() {
        EventObjective obj = junctionObjective(12, 0);
        var snapshots = List.of(
                snapshot(Map.of(), Map.of(), Map.of(ZONE, 4), Map.of()),
                snapshot(Map.of(), Map.of(), Map.of(ZONE, 3), Map.of())
        );

        EventObjective result = EventObjectiveTracker.recomputeJunction(obj, snapshots);

        assertEquals(7, result.current());
        assertFalse(result.failed());
    }

    @Test
    void gridlockFallaAlSuperarElUmbral() {
        EventObjective obj = junctionObjective(12, 0);
        var snapshots = List.of(snapshot(Map.of(), Map.of(), Map.of(ZONE, 13), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeJunction(obj, snapshots);

        assertEquals(13, result.current());
        assertTrue(result.failed());
    }

    @Test
    void gridlockNoFallaJustoEnElUmbral() {
        EventObjective obj = junctionObjective(12, 0);
        var snapshots = List.of(snapshot(Map.of(), Map.of(), Map.of(ZONE, 12), Map.of()));

        EventObjective result = EventObjectiveTracker.recomputeJunction(obj, snapshots);

        assertEquals(12, result.current());
        assertFalse(result.failed());
    }
}
