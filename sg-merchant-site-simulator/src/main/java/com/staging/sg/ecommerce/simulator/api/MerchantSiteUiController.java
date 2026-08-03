package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.ecommerce.simulator.service.EcommerceSimulatorClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant-site-simulator/v1/ui")
public class MerchantSiteUiController {
    private final EcommerceSimulatorClient client;
    private final Path profileIdFile;

    public MerchantSiteUiController(EcommerceSimulatorClient client,
            @Value("${ecommerce-simulator.ui.profile-id-file:${user.dir}/runtime/acquiring-ecommerce-e2e/profile-id}")
            String profileIdFile) {
        this.client = client;
        this.profileIdFile = Path.of(profileIdFile);
    }

    @GetMapping("/configuration")
    public ResponseEntity<?> configuration() {
        try {
            UUID profileId = UUID.fromString(Files.readString(profileIdFile).trim());
            return ResponseEntity.ok(Map.of(
                    "profileId", profileId,
                    "acquirerId", "ACQECOM",
                    "currency", "504",
                    "sandbox", true));
        } catch (Exception exception) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Le profil e-commerce n'est pas provisionne"));
        }
    }

    @PostMapping("/checkouts")
    public ResponseEntity<?> start(@RequestBody SimulatorPurchaseRequest request) {
        try {
            return ResponseEntity.ok(client.startInteractive(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Le checkout n'a pas pu etre initialise"));
        }
    }

    @PostMapping("/checkouts/{checkoutId}/complete")
    public ResponseEntity<?> complete(@PathVariable UUID checkoutId) {
        try {
            return ResponseEntity.ok(client.completeInteractive(checkoutId));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
