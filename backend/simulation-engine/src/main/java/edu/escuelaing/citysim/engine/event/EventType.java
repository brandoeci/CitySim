package edu.escuelaing.citysim.engine.event;

/**
 * Tipos de evento colaborativo. Cada tipo define la accion que lo resuelve,
 * de modo que las respuestas de los administradores de zona deben coincidir
 * con la accion requerida para contar (HU11).
 */
public enum EventType {

    TRAFFIC_JAM(EventAction.REDIRECT_TRAFFIC),
    ACCIDENT(EventAction.CLEAR_ROAD),
    ROAD_CLOSURE(EventAction.REROUTE),
    VIP_CONVOY(EventAction.ESCORT),
    EMERGENCY(EventAction.DISPATCH_UNITS);

    private final EventAction requiredAction;

    EventType(EventAction requiredAction) {
        this.requiredAction = requiredAction;
    }

    public EventAction getRequiredAction() {
        return requiredAction;
    }
}
