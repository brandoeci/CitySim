package edu.escuelaing.citysim.engine.zone;

import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZoneAssignmentServiceTest {

    @Mock private ZoneRegistry zoneRegistry;
    @Mock private SpaceDataGrid space;

    private ZoneAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new ZoneAssignmentService(zoneRegistry, space);
    }

    private Map<String, ZoneProcessingUnit> zonesFor(String... ids) {
        Map<String, ZoneProcessingUnit> zones = new LinkedHashMap<>();
        for (String id : ids) zones.put(id, null); // solo importan las keys
        return zones;
    }

    @Test
    void usuarioNuevoRecibeUnaZona() {
        when(space.getAssignedZone("nuevo")).thenReturn(null);
        when(space.getAllZoneAssignments()).thenReturn(Map.of());
        when(zoneRegistry.getOwnedZones()).thenReturn(zonesFor("Z_0_0", "Z_0_1"));

        String zone = service.assignZone("nuevo");

        assertNotNull(zone);
        assertTrue(List.of("Z_0_0", "Z_0_1").contains(zone));
        verify(space).assignZone("nuevo", zone);
    }

    @Test
    void usuarioYaAsignadoRecibeLaMismaZona() {
        when(space.getAssignedZone("existente")).thenReturn("Z_1_1");

        String zone = service.assignZone("existente");

        assertEquals("Z_1_1", zone);
        // No debe reasignar
        verify(space, never()).assignZone(anyString(), anyString());
    }

    @Test
    void laAsignacionBalanceaHaciaLaZonaConMenosUsuarios() {
        when(space.getAssignedZone("nuevo")).thenReturn(null);
        // Z_0_0 ya tiene 2 usuarios, Z_0_1 tiene 0
        when(space.getAllZoneAssignments()).thenReturn(Map.of(
                "userA", "Z_0_0",
                "userB", "Z_0_0"
        ));
        when(zoneRegistry.getOwnedZones()).thenReturn(zonesFor("Z_0_0", "Z_0_1"));

        String zone = service.assignZone("nuevo");

        // Debe elegir la zona vacia
        assertEquals("Z_0_1", zone);
    }

    @Test
    void getZoneDelegaEnElSpace() {
        when(space.getAssignedZone("hildebrando")).thenReturn("Z_2_2");

        assertEquals("Z_2_2", service.getZone("hildebrando"));
    }

    @Test
    void getAllAssignmentsDelegaEnElSpace() {
        Map<String, String> assignments = Map.of("user1", "Z_0_0", "user2", "Z_1_1");
        when(space.getAllZoneAssignments()).thenReturn(assignments);

        assertEquals(assignments, service.getAllAssignments());
    }
}
