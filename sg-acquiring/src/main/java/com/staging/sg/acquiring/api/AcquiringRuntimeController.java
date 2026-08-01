package com.staging.sg.acquiring.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/acquiring/v1")
public class AcquiringRuntimeController {
    private final boolean serverPosProjectionEnabled;

    public AcquiringRuntimeController(
            @Value("${acquiring.server-pos.enabled:false}")
            boolean serverPosProjectionEnabled) {
        this.serverPosProjectionEnabled = serverPosProjectionEnabled;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "sg-acquiring");
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
                "contractModel", "payment_contract",
                "contractTypes", List.of(
                        "ISSUING_CARD", "ACQUIRING_MERCHANT", "ACQUIRING_DEVICE"),
                "channels", List.of("TPE", "ECOMMERCE"),
                "serverPosProjection", serverPosProjectionEnabled,
                "financialAuthorization", true,
                "ecommerceRoutes", List.of("DMAS_MASTERCARD", "SWAM"),
                "threeDS", false,
                "clearingSettlement", false);
    }
}
