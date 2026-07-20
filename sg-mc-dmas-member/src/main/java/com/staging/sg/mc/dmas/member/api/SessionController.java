package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.mc.dmas.member.network.SessionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pilotage de la session DMAS (orchestration sign-on -> key exchange -> echo).
 * Protégé ADMIN via /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/dmas/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionOrchestrator orchestrator;

    public SessionController(SessionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** Démarre la session complète : sign-on -> PEK exchange -> echo. */
    @PostMapping("/start")
    public ResponseEntity<?> start() {
        try {
            return ResponseEntity.ok(orchestrator.startSession());
        } catch (Exception e) {
            log.error("[DMAS-SESSION] start failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** Ferme la session : sign-off. */
    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        try {
            return ResponseEntity.ok(orchestrator.stopSession());
        } catch (Exception e) {
            log.error("[DMAS-SESSION] stop failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
