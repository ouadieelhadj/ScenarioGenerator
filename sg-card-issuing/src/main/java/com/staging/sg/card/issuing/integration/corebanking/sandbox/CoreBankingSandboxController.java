package com.staging.sg.card.issuing.integration.corebanking.sandbox;

import com.staging.sg.card.issuing.integration.corebanking.CoreBankingAuthorizationRequest;
import com.staging.sg.card.issuing.integration.corebanking.CoreBankingAuthorizationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sandbox/core-banking/v1")
@ConditionalOnProperty(
        name = "issuing.core-banking.sandbox.enabled",
        havingValue = "true")
public class CoreBankingSandboxController {
    private final CoreBankingSandboxService sandbox;

    public CoreBankingSandboxController(CoreBankingSandboxService sandbox) {
        this.sandbox = sandbox;
    }

    @PutMapping("/accounts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putAccount(
            @RequestParam String fundingContractId,
            @RequestBody CoreBankingSandboxAccountRequest request) {
        sandbox.putAccount(fundingContractId, request);
    }

    @PostMapping("/authorizations")
    public CoreBankingAuthorizationResponse authorize(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestBody CoreBankingAuthorizationRequest request) {
        if (!idempotencyKey.equals(request.idempotencyKey())
                || !correlationId.equals(request.correlationId())) {
            throw new IllegalArgumentException(
                    "Headers and request identifiers must match");
        }
        return sandbox.authorize(request);
    }
}
