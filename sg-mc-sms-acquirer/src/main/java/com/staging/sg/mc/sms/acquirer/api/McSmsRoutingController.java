package com.staging.sg.mc.sms.acquirer.api;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.mc.sms.acquirer.network.McSmsFinancialService;
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
public class McSmsRoutingController {
    private static final String ROUTE = "MASTERCARD_SMS";
    private final McSmsFinancialService financial;
    private final McNetworkController network;

    public McSmsRoutingController(
            McSmsFinancialService financial, McNetworkController network) {
        this.financial = financial;
        this.network = network;
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> transact(@RequestBody RoutingTransactionRequest request) {
        try {
            Map<String, Object> result = financial.send(request);
            String rc = result.get("response_code") == null
                    ? null : result.get("response_code").toString();
            boolean approved = Boolean.TRUE.equals(result.get("approved"));
            return ResponseEntity.ok(new RoutingTransactionResponse(
                    request.transactionId(), approved ? "APPROVED" : "DECLINED",
                    rc, rc, value(result, "authorization_code"), ROUTE,
                    approved ? request.amount() : null,
                    value(result, "de55_response_hex"), false,
                    Map.of("network", "MASTERCARD_SMS")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("schemaVersion", "1.0", "network", ROUTE,
                "operations", List.of("FINANCIAL", "ADVICE", "REVERSAL"));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return network.health();
    }

    private static String value(Map<String, Object> map, String key) {
        return map.get(key) == null ? null : map.get(key).toString();
    }
}
