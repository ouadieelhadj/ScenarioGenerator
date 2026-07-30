package com.staging.sg.swam.acquirer.api;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
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
public class SwamRoutingController {
    private static final String ROUTE = "SWAM_MEMBER";
    private final SwamNetworkController network;

    public SwamRoutingController(SwamNetworkController network) {
        this.network = network;
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> transact(@RequestBody RoutingTransactionRequest request) {
        try {
            Map<String, Object> result = network.sendRouted(request);
            String rc = result.get("de39_action") == null
                    ? null : result.get("de39_action").toString();
            boolean approved = "000".equals(rc);
            return ResponseEntity.ok(new RoutingTransactionResponse(
                    request.transactionId(), approved ? "APPROVED" : "DECLINED",
                    mapPosCode(rc), rc,
                    result.get("de38_auth") == null ? null : result.get("de38_auth").toString(),
                    ROUTE, approved ? request.amount() : null,
                    result.get("de55_response_hex") == null ? null
                            : result.get("de55_response_hex").toString(),
                    false,
                    Map.of("network", "SWAM")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("schemaVersion", "1.0", "network", ROUTE,
                "operations", List.of("AUTHORIZATION", "FINANCIAL", "ADVICE", "REVERSAL"));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return network.health();
    }

    private static String mapPosCode(String swamCode) {
        if ("000".equals(swamCode)) return "00";
        if ("116".equals(swamCode)) return "51";
        if ("114".equals(swamCode)) return "14";
        if ("117".equals(swamCode)) return "55";
        return "05";
    }
}
