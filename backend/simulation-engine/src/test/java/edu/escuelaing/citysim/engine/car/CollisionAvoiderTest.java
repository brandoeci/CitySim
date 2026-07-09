package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.model.CarState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollisionAvoiderTest {

    private CarState carAt(String id, String edgeId, int lane, double offset) {
        return CarState.builder()
                .carId(id)
                .currentEdgeId(edgeId)
                .laneIndex(lane)
                .segmentOffset(offset)
                .build();
    }

    @Test
    void unCarroSoloNoEstaBloqueado() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        avoider.register(carAt("car-1", "E1", 0, 0.5));

        // No hay nadie adelante en offset 0.3
        assertFalse(avoider.isBlocked("E1", 0, 0.3));
    }

    @Test
    void detectaCarroAdelanteDentroDeLaDistanciaMinima() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        avoider.register(carAt("car-adelante", "E1", 0, 0.5));

        // Un carro que intenta avanzar a 0.4 (a 0.1 del de adelante < 0.15) esta bloqueado
        assertTrue(avoider.isBlocked("E1", 0, 0.4));
    }

    @Test
    void carroLejosNoEstaBloqueado() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        avoider.register(carAt("car-adelante", "E1", 0, 0.9));

        // A 0.3 hay 0.6 de distancia, mayor que 0.15
        assertFalse(avoider.isBlocked("E1", 0, 0.3));
    }

    @Test
    void carrilesDistintosNoSeBloquean() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        avoider.register(carAt("car-carril-0", "E1", 0, 0.5));

        // Un carro en el carril 1 no ve al del carril 0
        assertFalse(avoider.isBlocked("E1", 1, 0.4));
    }

    @Test
    void aristasDistintasNoSeBloquean() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        avoider.register(carAt("car-E1", "E1", 0, 0.5));

        // Un carro en E2 no ve al de E1
        assertFalse(avoider.isBlocked("E2", 0, 0.4));
    }

    @Test
    void unregisterEliminaElCarro() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        CarState car = carAt("car-1", "E1", 0, 0.5);
        avoider.register(car);
        avoider.unregister(car);

        // Ya no bloquea porque se desregistro
        assertFalse(avoider.isBlocked("E1", 0, 0.4));
    }

    @Test
    void updateMueveElCarroDePosicion() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        CarState oldState = carAt("car-1", "E1", 0, 0.3);
        CarState newState = carAt("car-1", "E1", 0, 0.5);

        avoider.register(oldState);
        avoider.update(oldState, newState);

        // En la posicion vieja ya no bloquea
        assertFalse(avoider.isBlocked("E1", 0, 0.2));
        // En la nueva posicion si bloquea a quien venga cerca
        assertTrue(avoider.isBlocked("E1", 0, 0.4));
    }

    @Test
    void updateEntreAristasDistintasReubicaElCarro() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        CarState oldState = carAt("car-1", "E1", 0, 0.5);
        CarState newState = carAt("car-1", "E2", 0, 0.1);

        avoider.register(oldState);
        avoider.update(oldState, newState);

        // Ya no esta en E1
        assertFalse(avoider.isBlocked("E1", 0, 0.4));
        // Ahora esta en E2
        assertTrue(avoider.isBlocked("E2", 0, 0.05));
    }

    @Test
    void carroSinAristaNoSeRegistra() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        CarState car = CarState.builder().carId("car-1").laneIndex(0).segmentOffset(0.5).build();
        // currentEdgeId es null
        avoider.register(car);

        // No hay nada registrado
        assertFalse(avoider.isBlocked("E1", 0, 0.4));
    }

    @Test
    void clearEliminaTodo() {
        CollisionAvoider avoider = new CollisionAvoider(0.15);
        avoider.register(carAt("car-1", "E1", 0, 0.5));
        avoider.register(carAt("car-2", "E2", 0, 0.5));

        avoider.clear();

        assertFalse(avoider.isBlocked("E1", 0, 0.4));
        assertFalse(avoider.isBlocked("E2", 0, 0.4));
    }
}
