package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.mc.dmas.member.network.McDmasAdvice;
import com.staging.sg.mc.dmas.member.network.McDmasAuthorization;
import com.staging.sg.mc.dmas.member.network.McDmasReversal;
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
public class McDmasRoutingController {
    private static final String ROUTE = "DMAS_MEMBER";
    private final McDmasAuthorization authorization;
    private final McDmasAdvice advice;
    private final McDmasReversal reversal;

    public McDmasRoutingController(
            McDmasAuthorization authorization, McDmasAdvice advice,
            McDmasReversal reversal) {
        this.authorization = authorization;
        this.advice = advice;
        this.reversal = reversal;
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> transact(@RequestBody RoutingTransactionRequest request) {
        try {
            if (request.pinBlockHex() != null && !pinDomainIsRoute(request)) {
                return ResponseEntity.unprocessableEntity().body(Map.of(
                        "error", "PIN block is not in the DMAS PEK domain"));
            }
            Map<String, Object> result = dispatch(request);
            String rc = text(result, "de039_response_code");
            boolean approved = Boolean.TRUE.equals(result.get("approved"))
                    || Boolean.TRUE.equals(result.get("reversed"))
                    || Boolean.TRUE.equals(result.get("acknowledged"))
                    || "00".equals(rc);
            return ResponseEntity.ok(new RoutingTransactionResponse(
                    request.transactionId(), approved ? "APPROVED" : "DECLINED",
                    rc, rc, text(result, "de038_authorization_code"), ROUTE,
                    approved ? request.amount() : null,
                    text(result, "de055_response_hex"), false,
                    Map.of("network", "DMAS")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> dispatch(RoutingTransactionRequest request) throws Exception {
        String operation = request.operation() == null ? "" : request.operation();
        if ("REVERSAL".equals(operation)) {
            String originalStan = attribute(request, "originalStan", request.stan());
            String originalDt = attribute(request, "originalTransmissionDateTime", null);
            boolean adviceMode = request.sourceMti() != null && request.sourceMti().startsWith("042");
            return adviceMode
                    ? reversal.sendReversalAdvice(request.pan(), request.amount(),
                        request.processingCode(), originalStan, originalDt)
                    : reversal.sendReversal(request.pan(), request.amount(),
                        request.processingCode(), originalStan, originalDt);
        }
        if ("ADVICE".equals(operation)) {
            return advice.sendAdvice(request.pan(), request.amount(),
                    request.processingCode(), null, request.terminalId(), request.merchantId());
        }
        return authorization.sendRoutedAuthorization(
                dmasType(request.processingCode()), request.sourceMti(),
                request.processingCode(), request.pan(), request.expiry(),
                request.amount(), request.currency(),
                request.pinBlockHex() == null ? null
                        : org.jpos.iso.ISOUtil.hex2byte(request.pinBlockHex()),
                request.emvDataHex(), request.terminalId(), request.merchantId(),
                attribute(request, "entryMode", null),
                attribute(request, "conditionCode", null), request.rrn());
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("schemaVersion", "1.0", "network", ROUTE,
                "operations", List.of("AUTHORIZATION", "ADVICE", "REVERSAL"));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "module", ROUTE);
    }

    private static String dmasType(String processingCode) {
        String prefix = processingCode == null || processingCode.length() < 2
                ? "00" : processingCode.substring(0, 2);
        return switch (prefix) {
            case "01" -> "WITHDRAWAL";
            case "09" -> "PURCHASE_CASHBACK";
            case "17" -> "CASH_DISBURSEMENT";
            case "20" -> "REFUND";
            case "28" -> "PAYMENT";
            case "30" -> "BALANCE_INQUIRY";
            case "40" -> "TRANSFER";
            case "91" -> "PIN_UNBLOCK";
            case "92" -> "PIN_CHANGE";
            default -> "PURCHASE";
        };
    }

    private static String attribute(
            RoutingTransactionRequest request, String name, String fallback) {
        return request.attributes() == null
                ? fallback : request.attributes().getOrDefault(name, fallback);
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean pinDomainIsRoute(RoutingTransactionRequest request) {
        return request.attributes() != null
                && ROUTE.equals(request.attributes().get("pinBlockKeyDomain"));
    }
}
