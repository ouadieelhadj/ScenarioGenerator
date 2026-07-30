package com.staging.sg.waypos.server.api;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.waypos.server.service.PosRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routing/v1")
public class WayPosRoutingController {
    private final PosRoutingService service;

    public WayPosRoutingController(PosRoutingService service) {
        this.service = service;
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> transact(@RequestBody RoutingTransactionRequest request) {
        try {
            return ResponseEntity.ok(service.process(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
                "schemaVersion", "1.0",
                "basicSet", true,
                "extendedSet", true,
                "macData", List.of("BIN", "HEX"),
                "keyBlock", "ANSI_X9_17");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "module", "WayPosServer");
    }
}
