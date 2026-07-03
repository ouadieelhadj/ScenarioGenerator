package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.config.RestartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gestion du port du service (consultation + modification avec redemarrage).
 * La route /api/admin/** est deja protegee par hasRole("ADMIN") dans SecurityConfig,
 * donc ces endpoints exigent un token ADMIN.
 */
@RestController
@RequestMapping("/api/admin/config")
public class PortConfigController {

    private static final Logger log = LoggerFactory.getLogger(PortConfigController.class);

    private final RestartService restartService;

    @Value("${server.port}")
    private int currentPort;

    public PortConfigController(RestartService restartService) {
        this.restartService = restartService;
    }

    /** Port actuel du service. */
    @GetMapping("/port")
    public ResponseEntity<?> getPort() {
        return ResponseEntity.ok(Map.of(
            "service", "orchestrator",
            "currentPort", currentPort
        ));
    }

    /** Change le port et redemarre le service. */
    @PostMapping("/port")
    public ResponseEntity<?> setPort(@RequestBody Map<String, Integer> body) {
        Integer newPort = body.get("port");
        if (newPort == null || newPort < 1024 || newPort > 65535) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Port invalide (attendu 1024-65535)"
            ));
        }
        if (newPort == currentPort) {
            return ResponseEntity.ok(Map.of(
                "message", "Le service ecoute deja sur ce port",
                "currentPort", currentPort,
                "restarting", false
            ));
        }
        log.info("[CONFIG] Demande de changement de port : {} -> {}", currentPort, newPort);
        restartService.changePortAndRestart(newPort);
        return ResponseEntity.ok(Map.of(
            "message", "Redemarrage en cours",
            "oldPort", currentPort,
            "newPort", newPort,
            "restarting", true
        ));
    }
}

