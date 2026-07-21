package edu.escuelaing.citysim.engine.space;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica el prefijo de sala de HazelcastSpaceDataGrid contra una instancia
 * Hazelcast real y embebida (sin red, sin mocks): que dos salas no se
 * mezclen, que el conteo de presencia suba/baje bien, y que la comprobacion
 * de cupo que usa RoomService.join() (`!isActive && count >= maxPlayers`) se
 * comporte correctamente en el limite.
 */
class HazelcastSpaceDataGridRoomPrefixTest {

    private static HazelcastInstance hazelcast;

    @BeforeAll
    static void setUp() {
        Config config = new Config();
        config.setClusterName("room-prefix-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcast = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void tearDown() {
        hazelcast.shutdown();
    }

    @Test
    void dosSalasConElMismoUsuarioNoSeMezclan() {
        HazelcastSpaceDataGrid roomA = new HazelcastSpaceDataGrid(hazelcast, "room:AAA111:");
        HazelcastSpaceDataGrid roomB = new HazelcastSpaceDataGrid(hazelcast, "room:BBB222:");

        roomA.heartbeat("alice");
        roomA.heartbeat("bob");
        roomB.heartbeat("alice");   // mismo username, sala distinta

        assertEquals(2, roomA.getActiveUserCount());
        assertEquals(1, roomB.getActiveUserCount());
    }

    @Test
    void elContadorDeUsuariosActivosSubeYBajaCorrectamente() {
        HazelcastSpaceDataGrid room = new HazelcastSpaceDataGrid(hazelcast, "room:CCC333:");

        for (int i = 0; i < 6; i++) {
            room.heartbeat("user" + i);
        }
        assertEquals(6, room.getActiveUserCount());

        // Exactamente la condicion que usa RoomService.join() para el cupo.
        boolean seventhRejected = !room.isActive("user6") && room.getActiveUserCount() >= 6;
        assertTrue(seventhRejected, "un septimo usuario nuevo deberia ser rechazado con cupo 6");

        // Un usuario YA activo (recarga de pagina) no cuenta contra el cupo.
        boolean existingUserAllowed = !room.isActive("user0") && room.getActiveUserCount() >= 6;
        assertFalse(existingUserAllowed, "un usuario ya activo no deberia contar contra el cupo otra vez");

        room.removePresence("user0");
        assertEquals(5, room.getActiveUserCount());
    }

    @Test
    void noAfectaElMapaGlobalSinPrefijo() {
        HazelcastSpaceDataGrid global = new HazelcastSpaceDataGrid(hazelcast); // prefijo vacio, el de siempre
        HazelcastSpaceDataGrid room = new HazelcastSpaceDataGrid(hazelcast, "room:DDD444:");

        room.heartbeat("carol");

        assertEquals(0, global.getActiveUserCount());
        assertEquals(1, room.getActiveUserCount());
    }
}
