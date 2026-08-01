package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.ecommerce.simulator.service.EcommerceSimulatorClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ecommerce-simulator/v1")
public class EcommerceSimulatorController {
    private final EcommerceSimulatorClient client;

    public EcommerceSimulatorController(EcommerceSimulatorClient client) {
        this.client = client;
    }

    @PostMapping("/purchases")
    public ResponseEntity<?> purchase(@RequestBody SimulatorPurchaseRequest request) {
        try {
            return ResponseEntity.ok(client.purchase(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "module", "sg-ecommerce-simulator",
                "threeDS", false, "routes", new String[] {"DMAS_MASTERCARD", "SWAM"});
    }
}
