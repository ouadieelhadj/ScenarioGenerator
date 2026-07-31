package com.staging.sg.card.issuing.service;

import com.staging.sg.card.issuing.api.CardProductRepresentation;
import com.staging.sg.card.issuing.api.CreateCardProductRequest;
import com.staging.sg.card.issuing.domain.CardProduct;
import com.staging.sg.card.issuing.domain.OutboxEvent;
import com.staging.sg.card.issuing.repository.CardProductRepository;
import com.staging.sg.card.issuing.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CardProductService {
    private final CardProductRepository products;
    private final OutboxEventRepository outbox;

    public CardProductService(
            CardProductRepository products, OutboxEventRepository outbox) {
        this.products = products;
        this.outbox = outbox;
    }

    @Transactional
    public CardProductRepresentation create(
            CreateCardProductRequest request, String callerId,
            String idempotencyKey, String correlationId) {
        String fingerprint = CommandFingerprint.of(
                request.issuerId(), request.productCode(), request.productVersion(),
                request.cardType(), request.currency(), request.purchaseEnabled(),
                request.cashEnabled(), request.ecommerceEnabled());
        var existing = products
                .findByIssuerIdAndCreatedByAndCreationIdempotencyKey(
                        request.issuerId(), callerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().creationMatches(fingerprint)) {
                throw new IllegalStateException(
                        "Idempotency key already used with another product payload");
            }
            return CardProductRepresentation.from(existing.get(), true);
        }
        CardProduct product = CardProduct.draft(
                request.issuerId(), request.productCode(),
                request.productVersion(), request.cardType(), request.currency(),
                request.purchaseEnabled(), request.cashEnabled(),
                request.ecommerceEnabled(), callerId, idempotencyKey, fingerprint);
        products.save(product);
        emit(product, "CardProductCreated", correlationId);
        return CardProductRepresentation.from(product, false);
    }

    @Transactional
    public CardProductRepresentation approve(
            UUID id, String issuerId, String approver, String correlationId) {
        CardProduct product = owned(id, issuerId);
        if (product.approve(approver)) {
            products.save(product);
            emit(product, "CardProductApproved", correlationId);
        }
        return CardProductRepresentation.from(product, false);
    }

    @Transactional
    public CardProductRepresentation activate(
            UUID id, String issuerId, String correlationId) {
        CardProduct product = owned(id, issuerId);
        if (product.activate()) {
            products.save(product);
            emit(product, "CardProductActivated", correlationId);
        }
        return CardProductRepresentation.from(product, false);
    }

    private CardProduct owned(UUID id, String issuerId) {
        CardProduct product = products.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown card product"));
        if (!product.issuerId().equals(issuerId)) {
            throw new IllegalArgumentException("Unknown card product");
        }
        return product;
    }

    private void emit(CardProduct product, String eventType, String correlationId) {
        String payload = "{\"issuerId\":\"" + safe(product.issuerId())
                + "\",\"productId\":\"" + product.id()
                + "\",\"productCode\":\"" + safe(product.productCode())
                + "\",\"status\":\"" + product.status() + "\"}";
        outbox.save(OutboxEvent.pending(
                "CardProduct", product.id().toString(), eventType,
                correlationId, payload));
    }

    private static String safe(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
