package com.staging.sg.dmas.issuer.api;

import com.staging.sg.common.JwtService;
import com.staging.sg.common.dto.LoginRequest;
import com.staging.sg.common.dto.LoginResponse;
import com.staging.sg.common.entity.User;
import com.staging.sg.common.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService      = jwtService;
    }

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

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getLogin(), user.getRole().name());
        log.info("[AUTH] Login success — login={} role={}", user.getLogin(), user.getRole());

        return ResponseEntity.ok(new LoginResponse(
                token, user.getLogin(), user.getRole().name(), 86400000L));
    }
}
