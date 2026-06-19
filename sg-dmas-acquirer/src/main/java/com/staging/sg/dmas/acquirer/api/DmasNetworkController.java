package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.dmas.acquirer.network.McDmasNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pilotage réseau DMAS (sign-on / sign-off / echo).
 * Protégé ADMIN via la règle URL /api/admin/** du SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/dmas/network")
public class DmasNetworkController {

    private static final Logger log = LoggerFactory.getLogger(DmasNetworkController.class);

    private final McDmasNetworkManager network;

    public DmasNetworkController(McDmasNetworkManager network) {
        this.network = network;
    }

    @PostMapping("/signon")
    public ResponseEntity<?> signon() {
        return run(network::sendSignOn, "signon");
    }

    @PostMapping("/signoff")
    public ResponseEntity<?> signoff() {
        return run(network::sendSignOff, "signoff");
    }

    @PostMapping("/echo")
    public ResponseEntity<?> echo() {
        return run(network::sendEcho, "echo");
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("signed_on", network.isSignedOn()));
    }

    private interface NetworkCall { Map<String,Object> call() throws Exception; }

    private ResponseEntity<?> run(NetworkCall c, String op) {
        try {
            return ResponseEntity.ok(c.call());
        } catch (Exception e) {
            log.error("[DMAS-ACQ] {} failed : {}", op, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
