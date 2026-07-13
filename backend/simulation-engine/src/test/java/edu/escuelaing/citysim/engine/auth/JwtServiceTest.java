package edu.escuelaing.citysim.engine.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inyectar el secret manualmente (normalmente lo pone @Value)
        ReflectionTestUtils.setField(jwtService, "secret",
                "citysim-secret-key-must-be-at-least-256-bits-long-for-hs256-ok");
    }

    @Test
    void generateProduceUnTokenNoVacio() {
        String token = jwtService.generate("hildebrando");

        assertNotNull(token);
        assertFalse(token.isBlank());
        // Un JWT tiene 3 partes separadas por punto
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void extractUsernameRecuperaElUsuarioDelToken() {
        String token = jwtService.generate("hildebrando");
        String username = jwtService.extractUsername(token);

        assertEquals("hildebrando", username);
    }

    @Test
    void isValidEsTrueParaUnTokenPropio() {
        String token = jwtService.generate("hildebrando");
        assertTrue(jwtService.isValid(token));
    }

    @Test
    void isValidEsFalseParaUnTokenCorrupto() {
        assertFalse(jwtService.isValid("token.completamente.invalido"));
    }

    @Test
    void isValidEsFalseParaUnaCadenaVacia() {
        assertFalse(jwtService.isValid(""));
    }

    @Test
    void isValidEsFalseParaUnTokenFirmadoConOtraClave() {
        // Generar con otro service que tiene distinto secret
        JwtService otro = new JwtService();
        ReflectionTestUtils.setField(otro, "secret",
                "otra-clave-distinta-de-al-menos-256-bits-de-longitud-abcdef");
        String tokenAjeno = otro.generate("intruso");

        // El service original no debe validarlo
        assertFalse(jwtService.isValid(tokenAjeno));
    }

    @Test
    void tokensDeUsuariosDistintosSonDistintos() {
        String t1 = jwtService.generate("usuario1");
        String t2 = jwtService.generate("usuario2");

        assertNotEquals(t1, t2);
        assertEquals("usuario1", jwtService.extractUsername(t1));
        assertEquals("usuario2", jwtService.extractUsername(t2));
    }
}
