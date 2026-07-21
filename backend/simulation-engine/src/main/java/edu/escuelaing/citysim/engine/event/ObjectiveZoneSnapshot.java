package edu.escuelaing.citysim.engine.event;

import java.io.Serializable;
import java.util.Map;

/**
 * Observacion local de una zona SBA sobre los objetivos activos del evento en
 * curso, escrita por la {@code ZoneProcessingUnit} que la posee (un solo
 * escritor por clave -> no requiere operaciones atomicas). Las claves de cada
 * mapa son el zoneId (cabecera de distrito) del objetivo correspondiente.
 */
public record ObjectiveZoneSnapshot(
        /** SHIELD_AREA: total acumulado de entradas al area desde que empezo el evento. */
        Map<String, Integer> areaEntryCumulative,
        /** CLEAR_CORRIDOR: si esta zona tiene ahora mismo un carro sobre el corredor. */
        Map<String, Boolean> corridorDirty,
        /** RELIEVE_JUNCTION: carros de esta zona esperando en el cruce ahora mismo. */
        Map<String, Integer> junctionWaiting,
        /** EVACUATE_AREA: carros de esta zona dentro del area ahora mismo. */
        Map<String, Integer> areaInside
) implements Serializable {}
