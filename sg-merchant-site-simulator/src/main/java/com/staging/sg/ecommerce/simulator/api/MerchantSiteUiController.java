package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.ecommerce.simulator.service.EcommerceSimulatorClient;
import com.staging.sg.ecommerce.simulator.service.MerchantStorefrontService;
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
    private final MerchantStorefrontService storefront;
    private final Path profileIdFile;

    public MerchantSiteUiController(EcommerceSimulatorClient client,
            MerchantStorefrontService storefront,
            @Value("${ecommerce-simulator.ui.profile-id-file:${user.dir}/runtime/acquiring-ecommerce-e2e/profile-id}")
            String profileIdFile) {
        this.client = client;
        this.storefront = storefront;
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
                    "siteType", storefront.siteType(),
                    "storeName", "Atlas Market",
                    "sandbox", true));
        } catch (Exception exception) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Le profil e-commerce n'est pas provisionne"));
        }
    }

    @GetMapping("/catalog")
    public Object catalog() {
        return storefront.catalog();
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody MerchantOrderCreateRequest request) {
        try {
            return ResponseEntity.ok(storefront.createOrder(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> order(@PathVariable UUID orderId) {
        try {
            return ResponseEntity.ok(storefront.order(orderId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<?> startOrderPayment(@PathVariable UUID orderId,
            @RequestBody MerchantCardPaymentRequest request) {
        try {
            return ResponseEntity.ok(storefront.startPayment(orderId, request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Le paiement marchand n'a pas pu etre initialise"));
        }
    }

    @PostMapping("/orders/payments/{checkoutId}/complete")
    public ResponseEntity<?> completeOrderPayment(@PathVariable UUID checkoutId) {
        try {
            return ResponseEntity.ok(storefront.completePayment(checkoutId));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
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
