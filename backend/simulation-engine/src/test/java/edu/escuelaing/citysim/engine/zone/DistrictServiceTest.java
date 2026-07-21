package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.Zone;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * DistrictService reparte el mapa en franjas verticales contiguas, una por
 * usuario activo. Estas pruebas fijan un grid de 4 columnas x 2 filas (8
 * zonas) y validan el algoritmo de particion directamente, sin necesidad de
 * contexto de Spring.
 */
@ExtendWith(MockitoExtension.class)
class DistrictServiceTest {

    @Mock
    private SpaceDataGrid space;

    private DistrictService service;

    @BeforeEach
    void setUp() {
        CityMap cityMap = new CityMap(Map.of(), Map.of(), buildZones(4, 2), 40, 20);
        service = new DistrictService(cityMap, space);
    }

    /** Grid de zoneCols x zoneRows zonas, ids "Z_<row>_<col>". */
    private static Map<String, Zone> buildZones(int cols, int rows) {
        Map<String, Zone> zones = new LinkedHashMap<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String id = "Z_" + row + "_" + col;
                zones.put(id, new Zone(id, row, col, 0, 0, 0, 0, Set.of(), Set.of()));
            }
        }
        return zones;
    }

    @Test
    void sinUsuariosActivosNoHayDistritos() {
        when(space.getActiveUsersOrdered()).thenReturn(List.of());

        assertTrue(service.getDistricts().isEmpty());
    }

    @Test
    void unSoloUsuarioAdministraTodoElMapa() {
        when(space.getActiveUsersOrdered()).thenReturn(List.of("ana"));

        List<District> districts = service.getDistricts();

        assertEquals(1, districts.size());
        District d = districts.get(0);
        assertEquals("ana", d.username());
        assertEquals(0, d.minCol());
        assertEquals(3, d.maxCol());
        assertEquals(8, d.zoneIds().size());
    }

    @Test
    void dosUsuariosParticionanElMapaEnFranjasContiguasYSinSolape() {
        when(space.getActiveUsersOrdered()).thenReturn(List.of("ana", "beto"));

        List<District> districts = service.getDistricts();

        assertEquals(2, districts.size());
        District first = districts.get(0);
        District second = districts.get(1);

        assertEquals(0, first.minCol());
        assertEquals(1, first.maxCol());
        assertEquals(2, second.minCol());
        assertEquals(3, second.maxCol());

        // Contiguos y sin solape: la segunda franja empieza justo donde termina la primera.
        assertEquals(first.maxCol() + 1, second.minCol());

        // Cada usuario recibe la mitad de las 8 zonas (2 columnas x 2 filas).
        assertEquals(4, first.zoneIds().size());
        assertEquals(4, second.zoneIds().size());
    }

    @Test
    void getDistrictOfEncuentraElDistritoDelUsuarioActivo() {
        when(space.getActiveUsersOrdered()).thenReturn(List.of("ana", "beto"));

        District mine = service.getDistrictOf("beto");

        assertNotNull(mine);
        assertEquals("beto", mine.username());
        assertEquals(1, mine.index());
    }

    @Test
    void getDistrictOfDevuelveNullParaUnUsuarioNoActivo() {
        when(space.getActiveUsersOrdered()).thenReturn(List.of("ana"));

        assertNull(service.getDistrictOf("fantasma"));
    }

    @Test
    void masUsuariosQueColumnasDejaAlgunosSinDistrito() {
        // 4 columnas, 5 usuarios: el quinto no cabe (una columna por usuario, como maximo).
        when(space.getActiveUsersOrdered())
                .thenReturn(List.of("a", "b", "c", "d", "e"));

        List<District> districts = service.getDistricts();

        assertEquals(4, districts.size());
        assertNull(service.getDistrictOf("e"));
    }

    @Test
    void isCityFullReflejaElTopeDeUsuariosActivos() {
        when(space.getActiveUserCount()).thenReturn(DistrictService.MAX_USERS - 1);
        assertFalse(service.isCityFull());

        when(space.getActiveUserCount()).thenReturn(DistrictService.MAX_USERS);
        assertTrue(service.isCityFull());
    }
}
