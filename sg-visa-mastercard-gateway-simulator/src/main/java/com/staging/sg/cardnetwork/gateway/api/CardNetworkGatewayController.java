package com.staging.sg.cardnetwork.gateway.api;

import com.staging.sg.cardnetwork.gateway.service.CardNetworkGatewayService;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.springframework.http.HttpStatus;
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
public class CardNetworkGatewayController {
    private final CardNetworkGatewayService service;

    public CardNetworkGatewayController(CardNetworkGatewayService service) {
        this.service = service;
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> route(@RequestBody RoutingTransactionRequest request) {
        try {
            RoutingTransactionResponse response = service.route(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (CardNetworkGatewayService.DownstreamUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage(), "retryable", false));
        }
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("schemaVersion", "1.0",
                "module", "VISA_MASTERCARD_GATEWAY_SIMULATOR",
                "programs", List.of("VISA", "MASTERCARD"),
                "role", "FINANCIAL_NETWORK_MULTIPLEXER");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP",
                "module", "VISA_MASTERCARD_GATEWAY_SIMULATOR");
    }
}
