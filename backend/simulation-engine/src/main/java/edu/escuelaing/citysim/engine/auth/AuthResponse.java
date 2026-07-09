package edu.escuelaing.citysim.engine.auth;

public record AuthResponse(String token, String username, String zoneId) {}