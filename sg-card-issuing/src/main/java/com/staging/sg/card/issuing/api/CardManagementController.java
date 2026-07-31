package com.staging.sg.card.issuing.api;

import com.staging.sg.card.issuing.service.CardContractService;
import com.staging.sg.card.issuing.service.CardIssuanceService;
import com.staging.sg.card.issuing.service.CardProductService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/issuing/v1")
public class CardManagementController {
    private final CardProductService products;
    private final CardContractService contracts;
    private final CardIssuanceService issuance;

    public CardManagementController(
            CardProductService products, CardContractService contracts,
            CardIssuanceService issuance) {
        this.products = products;
        this.contracts = contracts;
        this.issuance = issuance;
    }

    @PostMapping("/products")
    public CardProductRepresentation createProduct(
            @RequestHeader("X-Caller-ID") String callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestBody CreateCardProductRequest request) {
        return products.create(request, callerId, idempotencyKey, correlationId);
    }

    @PostMapping("/products/{id}/approve")
    public CardProductRepresentation approveProduct(
            @PathVariable UUID id,
            @RequestHeader("X-Issuer-ID") String issuerId,
            @RequestHeader("X-Caller-ID") String approver,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return products.approve(id, issuerId, approver, correlationId);
    }

    @PostMapping("/products/{id}/activate")
    public CardProductRepresentation activateProduct(
            @PathVariable UUID id,
            @RequestHeader("X-Issuer-ID") String issuerId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return products.activate(id, issuerId, correlationId);
    }

    @PostMapping("/contracts")
    public CardContractRepresentation createContract(
            @RequestHeader("X-Caller-ID") String callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestBody CreateCardContractRequest request) {
        return contracts.create(request, callerId, idempotencyKey, correlationId);
    }

    @PostMapping("/contracts/{id}/submit")
    public CardContractRepresentation submitContract(
            @PathVariable UUID id,
            @RequestHeader("X-Issuer-ID") String issuerId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return contracts.submit(id, issuerId, correlationId);
    }

    @PostMapping("/contracts/{id}/approve")
    public CardContractRepresentation approveContract(
            @PathVariable UUID id,
            @RequestHeader("X-Issuer-ID") String issuerId,
            @RequestHeader("X-Caller-ID") String approver,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return contracts.approve(id, issuerId, approver, correlationId);
    }

    @PostMapping("/contracts/{id}/cards/virtual")
    public CardInstrumentRepresentation issueVirtualCard(
            @PathVariable UUID id,
            @RequestHeader("X-Issuer-ID") String issuerId,
            @RequestHeader("X-Caller-ID") String callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return issuance.issueVirtual(
                id, issuerId, callerId, idempotencyKey, correlationId);
    }
}
