package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.service.IssuerAuthorizationUseCase;
import com.staging.sg.card.issuing.service.PreClearingValidationUseCase;
import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.IssuingAuthorizationResponse;
import com.staging.sg.common.issuing.PreClearingValidationRequest;
import com.staging.sg.common.issuing.PreClearingValidationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issuing/v1")
public class IssuingController {
    private final IssuerAuthorizationUseCase authorization;
    private final PreClearingValidationUseCase preClearing;

    public IssuingController(
            IssuerAuthorizationUseCase authorization,
            PreClearingValidationUseCase preClearing) {
        this.authorization = authorization;
        this.preClearing = preClearing;
    }

    @PostMapping("/authorizations")
    public IssuingAuthorizationResponse authorize(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestBody IssuingAuthorizationRequest request) {
        verifyHeaders(idempotencyKey, correlationId,
                request.idempotencyKey(), request.correlationId());
        return authorization.authorize(request);
    }

    @PostMapping("/pre-clearing/validations")
    public PreClearingValidationResponse validatePreClearing(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestBody PreClearingValidationRequest request) {
        verifyHeaders(idempotencyKey, correlationId,
                request.idempotencyKey(), request.correlationId());
        return preClearing.validate(request);
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
                "schemaVersions", List.of("1.0"),
                "decisionOwner", "sg-card-issuing",
                "callers", List.of("WAY_POS", "SWAM", "DMAS", "ECOMMERCE",
                        "PRE_CLEARING"),
                "authorizationStatus", "DEPENDENCIES_NOT_READY",
                "preClearingFinancialMutation", false);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "readiness", "NOT_READY",
                "service", "sg-card-issuing");
    }

    private static void verifyHeaders(
            String headerIdempotency, String headerCorrelation,
            String bodyIdempotency, String bodyCorrelation) {
        if (!headerIdempotency.equals(bodyIdempotency)
                || !headerCorrelation.equals(bodyCorrelation)) {
            throw new IllegalArgumentException(
                    "Headers and request identifiers must match");
        }
    }
}
