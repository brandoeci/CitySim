package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reparte la ciudad en distritos administrativos, uno por usuario activo.
 *
 * El grid de zonas SBA NO se toca: sigue siendo fijo y es el que da el
 * paralelismo (una ZoneProcessingUnit por zona). Lo que se calcula aqui es
 * una capa administrativa por encima: que franja del mapa administra cada
 * usuario. Con 2 usuarios activos el mapa se parte en 2 mitades, con 3 en
 * 3 franjas, etc. No existen distritos sin dueno.
 *
 * El reparto es por franjas verticales (columnas completas del grid), de modo
 * que cada distrito es contiguo y juntos cubren el mapa entero.
 */
@Service
public class DistrictService {

    /** Tope de usuarios simultaneos: la ciudad no admite mas administradores. */
    public static final int MAX_USERS = 6;

    private final CityMap cityMap;
    private final SpaceDataGrid space;

    public DistrictService(CityMap cityMap, SpaceDataGrid space) {
        this.cityMap = cityMap;
        this.space = space;
    }

    /**
     * Distritos actuales, calculados a partir de los usuarios activos
     * (los que mantienen su heartbeat vivo).
     */
    public List<District> getDistricts() {
        List<String> users = space.getActiveUsersOrdered();
        if (users.size() > MAX_USERS) {
            users = users.subList(0, MAX_USERS);
        }
        return partition(users);
    }

    /** Distrito de un usuario concreto, o null si no esta activo. */
    public District getDistrictOf(String username) {
        return getDistricts().stream()
                .filter(d -> d.username().equals(username))
                .findFirst()
                .orElse(null);
    }

    /** True si ya no caben mas administradores en la ciudad. */
    public boolean isCityFull() {
        return space.getActiveUserCount() >= MAX_USERS;
    }

    /**
     * Parte el grid en N franjas verticales contiguas, una por usuario.
     *
     * Con C columnas y N usuarios, al usuario i le corresponden las columnas
     * [floor(i*C/N), floor((i+1)*C/N) - 1]. El reparto siempre cubre todas las
     * columnas y ningun distrito queda vacio mientras N <= C.
     */
    private List<District> partition(List<String> users) {
        List<District> districts = new ArrayList<>();
        int n = users.size();
        if (n == 0) return districts;

        int totalCols = countColumns();
        if (totalCols == 0) return districts;

        // Si hubiera mas usuarios que columnas, no se puede dar una columna a cada uno.
        int effective = Math.min(n, totalCols);

        for (int i = 0; i < effective; i++) {
            int minCol = (int) Math.floor((double) i * totalCols / effective);
            int maxCol = (int) Math.floor((double) (i + 1) * totalCols / effective) - 1;

            final int lo = minCol;
            final int hi = maxCol;

            List<String> zoneIds = cityMap.getZones().values().stream()
                    .filter(z -> z.zoneCol() >= lo && z.zoneCol() <= hi)
                    .sorted(Comparator.comparingInt(Zone::zoneRow)
                            .thenComparingInt(Zone::zoneCol))
                    .map(Zone::id)
                    .collect(Collectors.toList());

            districts.add(new District(i, users.get(i), zoneIds, minCol, maxCol));
        }
        return districts;
    }

    /** Numero de columnas del grid de zonas. */
    private int countColumns() {
        return cityMap.getZones().values().stream()
                .mapToInt(Zone::zoneCol)
                .max()
                .orElse(-1) + 1;
    }
}