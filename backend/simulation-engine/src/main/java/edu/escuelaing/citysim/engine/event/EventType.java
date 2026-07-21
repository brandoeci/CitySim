package edu.escuelaing.citysim.engine.event;

import edu.escuelaing.citysim.core.model.ObjectiveKind;

/**
 * Tipos de evento colaborativo. Cada tipo define la mecanica de objetivo que
 * lo resuelve ({@link ObjectiveKind}); {@code requiredAction} es un campo
 * legado (HU11, no expuesto por ningun endpoint activo) que se conserva sin
 * tocar.
 */
public enum EventType {

    TRAFFIC_JAM(EventAction.REDIRECT_TRAFFIC, ObjectiveKind.CLOSE_EDGE),
    ACCIDENT(EventAction.CLEAR_ROAD, ObjectiveKind.CLOSE_EDGE),
    ROAD_CLOSURE(EventAction.REROUTE, ObjectiveKind.CLOSE_EDGE),
    EMERGENCY(EventAction.DISPATCH_UNITS, ObjectiveKind.CLOSE_EDGE),
    VIP_CONVOY(EventAction.ESCORT, ObjectiveKind.CLEAR_CORRIDOR),
    AREA_SHIELD(EventAction.REROUTE, ObjectiveKind.SHIELD_AREA),
    EVACUATION(EventAction.REROUTE, ObjectiveKind.EVACUATE_AREA),
    GRIDLOCK(EventAction.REROUTE, ObjectiveKind.RELIEVE_JUNCTION);

    private final EventAction requiredAction;
    private final ObjectiveKind objectiveKind;

    EventType(EventAction requiredAction, ObjectiveKind objectiveKind) {
        this.requiredAction = requiredAction;
        this.objectiveKind = objectiveKind;
    }

    public EventAction getRequiredAction() {
        return requiredAction;
    }

    public ObjectiveKind getObjectiveKind() {
        return objectiveKind;
    }
}
