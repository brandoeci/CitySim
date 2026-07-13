package edu.escuelaing.citysim.engine.zone;

import java.util.List;

/**
 * Distrito administrativo: el territorio que administra un usuario activo.
 *
 * Un distrito agrupa varias zonas del grid SBA (que sigue siendo fijo).
 * Los distritos se recalculan segun cuantos usuarios esten activos:
 * con 2 usuarios el mapa se parte en 2, con 3 en 3, y asi.
 *
 * @param index    posicion del distrito (0..N-1). Define el color en el frontend.
 * @param username usuario que lo administra.
 * @param zoneIds  ids de las zonas del grid que caen dentro del distrito.
 * @param minCol   primera columna del grid incluida (inclusive).
 * @param maxCol   ultima columna del grid incluida (inclusive).
 */
public record District(
        int index,
        String username,
        List<String> zoneIds,
        int minCol,
        int maxCol
) {}