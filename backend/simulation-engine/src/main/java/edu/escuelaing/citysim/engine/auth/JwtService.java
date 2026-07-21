package edu.escuelaing.citysim.engine.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret:citysim-secret-key-must-be-at-least-256-bits-long-for-hs256-ok}")
    private String secret;

    private static final long EXPIRATION_MS = 86400000L;

    public String generate(String username) {
        return generate(username, null);
    }

    /** Reemite el token con el claim "room" cuando el usuario se une a una sala. */
    public String generate(String username, String roomCode) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS));
        if (roomCode != null) {
            builder.claim("room", roomCode);
        }
        return builder.signWith(key).compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** La sala a la que se unio el usuario, o null si el token no la lleva. */
    public String extractRoomCode(String token) {
        return parseClaims(token).get("room", String.class);
    }

    public boolean isValid(String token) {
        try { parseClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}