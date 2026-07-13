package edu.escuelaing.citysim.engine.auth;

import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.DistrictService;
import edu.escuelaing.citysim.engine.zone.ZoneAssignmentService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ZoneAssignmentService zoneAssignment;
    private final DistrictService districtService;
    private final SpaceDataGrid space;

    public AuthService(UserRepository userRepository, JwtService jwtService,
                       PasswordEncoder passwordEncoder, ZoneAssignmentService zoneAssignment,
                       DistrictService districtService, SpaceDataGrid space) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.zoneAssignment = zoneAssignment;
        this.districtService = districtService;
        this.space = space;
    }

    public AuthResponse register(String username, String password) {
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("El usuario ya existe");

        // La ciudad solo admite MAX_USERS administradores simultaneos.
        if (districtService.isCityFull())
            throw new IllegalArgumentException(
                    "La ciudad esta llena (" + DistrictService.MAX_USERS +
                            " administradores). Intenta mas tarde.");

        String zoneId = zoneAssignment.assignZone(username);
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setZoneId(zoneId);
        userRepository.save(user);

        // Reclama el cupo de inmediato: sin esto, varios usuarios podrian
        // pasar la validacion antes de que ninguno registre su presencia.
        space.heartbeat(username);

        return new AuthResponse(jwtService.generate(username), username, zoneId);
    }

    public AuthResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas"));
        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new IllegalArgumentException("Credenciales invalidas");

        // Si el usuario YA esta activo (por ejemplo recargo la pagina), no
        // se le vuelve a cobrar cupo: el ya ocupa uno de los disponibles.
        if (!space.isActive(username) && districtService.isCityFull())
            throw new IllegalArgumentException(
                    "La ciudad esta llena (" + DistrictService.MAX_USERS +
                            " administradores). Intenta mas tarde.");

        space.heartbeat(username);

        return new AuthResponse(jwtService.generate(username), username, user.getZoneId());
    }
}