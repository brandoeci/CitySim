package edu.escuelaing.citysim.engine.event;

/**
 * Acciones que un administrador de zona puede ejecutar para responder a un evento.
 * Cada tipo de evento requiere una accion especifica para resolverse.
 */
public enum EventAction {
    REDIRECT_TRAFFIC,   // resuelve TRAFFIC_JAM
    CLEAR_ROAD,         // resuelve ACCIDENT
    REROUTE,            // resuelve ROAD_CLOSURE
    ESCORT,             // resuelve VIP_CONVOY
    DISPATCH_UNITS      // resuelve EMERGENCY
}
