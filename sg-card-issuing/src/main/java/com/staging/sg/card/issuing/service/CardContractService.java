package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.api.CardContractRepresentation;
import com.staging.sg.card.issuing.api.CreateCardContractRequest;
import com.staging.sg.card.issuing.domain.CardContract;
import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.domain.OutboxEvent;
import com.staging.sg.card.issuing.repository.CardContractRepository;
import com.staging.sg.card.issuing.repository.CardProductRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CardContractService {
    private final CardContractRepository contracts;
    private final CardProductRepository products;
    private final OutboxEventRepository outbox;

    public CardContractService(
            CardContractRepository contracts, CardProductRepository products,
            OutboxEventRepository outbox) {
        this.contracts = contracts;
        this.products = products;
        this.outbox = outbox;
    }

    @Transactional
    public CardContractRepresentation create(
            CreateCardContractRequest request, String callerId,
            String idempotencyKey, String correlationId) {
        CardProduct product = products.findById(request.productId())
                .filter(CardProduct::isActive)
                .filter(value -> value.issuerId().equals(request.issuerId()))
                .orElseThrow(() -> new IllegalStateException(
                        "An active product owned by the issuer is required"));
        String fingerprint = CommandFingerprint.of(
                request.issuerId(), request.externalReference(),
                request.customerId(), request.cardholderId(),
                request.fundingContractId(), request.productId());
        var existing = contracts
                .findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
                        request.issuerId(), callerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another contract payload");
            }
            return CardContractRepresentation.from(existing.get(), true);
        }
        CardContract contract = CardContract.draft(
                request.issuerId(), request.externalReference(),
                request.customerId(), request.cardholderId(),
                request.fundingContractId(), product.id(), callerId,
                idempotencyKey, fingerprint);
        contracts.save(contract);
        emit(contract, "CardContractCreated", correlationId);
        return CardContractRepresentation.from(contract, false);
    }

    @Transactional
    public CardContractRepresentation submit(
            UUID id, String issuerId, String correlationId) {
        CardContract contract = owned(id, issuerId);
        if (contract.submit()) {
            contracts.save(contract);
            emit(contract, "CardContractSubmitted", correlationId);
        }
        return CardContractRepresentation.from(contract, false);
    }

    @Transactional
    public CardContractRepresentation approve(
            UUID id, String issuerId, String approver, String correlationId) {
        CardContract contract = owned(id, issuerId);
        if (contract.approve(approver)) {
            contracts.save(contract);
            emit(contract, "CardContractApproved", correlationId);
        }
        return CardContractRepresentation.from(contract, false);
    }

    private CardContract owned(UUID id, String issuerId) {
        CardContract contract = contracts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown card contract"));
        if (!contract.issuerId().equals(issuerId)) {
            throw new IllegalArgumentException("Unknown card contract");
        }
        return contract;
    }

    private void emit(CardContract contract, String eventType, String correlationId) {
        String payload = "{\"issuerId\":\"" + safe(contract.issuerId())
                + "\",\"contractId\":\"" + contract.id()
                + "\",\"externalReference\":\""
                + safe(contract.externalReference())
                + "\",\"status\":\"" + contract.status() + "\"}";
        outbox.save(OutboxEvent.pending(
                "CardContract", contract.id().toString(), eventType,
                correlationId, payload));
    }

    private static String safe(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
