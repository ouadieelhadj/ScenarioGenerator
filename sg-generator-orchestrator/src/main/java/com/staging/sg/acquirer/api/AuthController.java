package com.staging.sg.acquirer.api;

import com.staging.sg.common.JwtService;
import com.staging.sg.common.dto.LoginRequest;
import com.staging.sg.common.dto.LoginResponse;
import com.staging.sg.common.entity.User;
import com.staging.sg.common.repository.UserRepository;
import com.staging.sg.common.repository.RoleRepository;
import com.staging.sg.common.entity.Role;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;
    private final RoleRepository  roleRepository;
    private final JdbcTemplate jdbcTemplate;

    public AuthController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          JdbcTemplate jdbcTemplate) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
        this.roleRepository  = roleRepository;
        this.jdbcTemplate    = jdbcTemplate;
    }

    // POST /auth/login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByLogin(req.getLogin())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("[AUTH] Login failed — login={}", req.getLogin());
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid login or password"));
        }

        if (!user.isActive()) {
            log.warn("[AUTH] Login failed — user inactive : {}", req.getLogin());
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account disabled"));
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        List<String> permissions = resolvePermissions(user);
        String token = jwtService.generateToken(user.getLogin(), user.getRole(), permissions);
        log.info("[AUTH] Login success — login={} role={}", user.getLogin(), user.getRole());

        return ResponseEntity.ok(new LoginResponse(
                token, user.getLogin(), user.getRole(), 86400000L));
    }

    private List<String> resolvePermissions(User user) {
        try {
            List<String> permissions = jdbcTemplate.queryForList("""
                    SELECT DISTINCT p.code
                      FROM user_profiles up
                      JOIN role_permissions rp ON rp.role_id=up.role_id
                      JOIN permissions p ON p.id=rp.permission_id
                     WHERE up.user_id=?
                     ORDER BY p.code
                    """, String.class, user.getId());
            if (!permissions.isEmpty()) return permissions;
        } catch (org.springframework.dao.DataAccessException ignored) {
            // Compatibilité avant application de la migration 18.
        }
        return roleRepository.findByCode(user.getRole())
                .map(r -> r.getPermissions().stream().map(p -> p.getCode())
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }
}
