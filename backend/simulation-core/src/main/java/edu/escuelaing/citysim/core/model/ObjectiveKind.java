package edu.escuelaing.citysim.core.model;

/**
 * Mecanica de un {@link EventObjective}.
 *
 * Los eventos "survival" (SHIELD_AREA, RELIEVE_JUNCTION) solo se resuelven al
 * agotarse el tiempo: si algun distrito falla antes, el evento entero termina
 * en FAILED de inmediato. Los eventos "achieve" (CLOSE_EDGE, CLEAR_CORRIDOR,
 * EVACUATE_AREA) se resuelven en cuanto todos los distritos cumplen su
 * objetivo, y si se acaba el tiempo sin lograrlo terminan en EXPIRED.
 */
public enum ObjectiveKind {
    CLOSE_EDGE(false),
    SHIELD_AREA(true),
    CLEAR_CORRIDOR(false),
    EVACUATE_AREA(false),
    RELIEVE_JUNCTION(true);

    private final boolean survival;

    ObjectiveKind(boolean survival) {
        this.survival = survival;
    }

    public boolean isSurvival() {
        return survival;
    }
}
