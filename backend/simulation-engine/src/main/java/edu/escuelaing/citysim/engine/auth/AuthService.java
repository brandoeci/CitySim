package edu.escuelaing.citysim.engine.auth;

import edu.escuelaing.citysim.engine.zone.ZoneAssignmentService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ZoneAssignmentService zoneAssignment;

    public AuthService(UserRepository userRepository, JwtService jwtService,
                       PasswordEncoder passwordEncoder, ZoneAssignmentService zoneAssignment) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.zoneAssignment = zoneAssignment;
    }

    public AuthResponse register(String username, String password) {
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("El usuario ya existe");
        String zoneId = zoneAssignment.assignZone(username);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setZoneId(zoneId);
        userRepository.save(user);
        return new AuthResponse(jwtService.generate(username), username, zoneId);
    }

    public AuthResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas"));
        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new IllegalArgumentException("Credenciales invalidas");
        return new AuthResponse(jwtService.generate(username), username, user.getZoneId());
    }
}