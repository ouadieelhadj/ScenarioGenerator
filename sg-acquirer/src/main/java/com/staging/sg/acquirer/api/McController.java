package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.acquirer.McAcquirer;
import com.staging.sg.acquirer.acquirer.McAuthRequest;
import com.staging.sg.acquirer.acquirer.McAuthResult;
import com.staging.sg.acquirer.network.McKeyExchangeResult;
import com.staging.sg.acquirer.network.McNetworkManager;
import com.staging.sg.acquirer.network.McNetworkResult;
import com.staging.sg.acquirer.network.McNetworkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class McController {

    private static final Logger log = LoggerFactory.getLogger(McController.class);

    private final McNetworkManager networkManager;
    private final McAcquirer       acquirer;

    public McController(McNetworkManager networkManager,
                        McAcquirer acquirer) {
        this.networkManager = networkManager;
        this.acquirer       = acquirer;
    }

    // GET /api/status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application",   "SG Acquirer");
        body.put("version",       "1.0.0-SNAPSHOT");
        body.put("keysExchanged", networkManager.isKeysExchanged());
        body.put("signedOn",      networkManager.isSignedOn());
        body.put("endpoints", Map.of(
                "authorize",     "POST /api/mc/authorize",
                "keyExchange",   "POST /api/mc/network/key-exchange",
                "signon",        "POST /api/mc/network/signon",
                "echo",          "POST /api/mc/network/echo",
                "networkStatus", "GET  /api/mc/network/status"
        ));
        return ResponseEntity.ok(body);
    }

    // POST /api/mc/authorize
    @PostMapping("/mc/authorize")
    public ResponseEntity<?> authorize(
            @RequestBody(required = false) McAuthRequest request) {
        if (request == null) request = new McAuthRequest();
        try {
            McAuthResult result = acquirer.authorize(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[API] Authorization error : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/mc/network/key-exchange
    @PostMapping("/mc/network/key-exchange")
    public ResponseEntity<?> keyExchange() {
        try {
            McKeyExchangeResult result = networkManager.performKeyExchange();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Key exchange error : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/mc/network/signon
    @PostMapping("/mc/network/signon")
    public ResponseEntity<?> signOn() {
        try {
            McNetworkResult result = networkManager.sendSignOn();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Sign-on error : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/mc/network/echo
    @PostMapping("/mc/network/echo")
    public ResponseEntity<?> echo() {
        try {
            McNetworkResult result = networkManager.sendEcho();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Echo error : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/mc/network/status
    @GetMapping("/mc/network/status")
    public ResponseEntity<McNetworkStatus> networkStatus() {
        return ResponseEntity.ok(networkManager.getStatus());
    }
}
