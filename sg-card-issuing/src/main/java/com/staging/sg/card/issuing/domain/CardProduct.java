package com.staging.sg.card.issuing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuing_card_product",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_issuing_product_version",
                        columnNames = {"issuer_id", "product_code", "product_version"}),
                @UniqueConstraint(name = "uk_issuing_product_idempotency",
                        columnNames = {"issuer_id", "created_by", "creation_idempotency_key"})
        })
public class CardProduct {
    @Id
    private UUID id;
    @Column(name = "issuer_id", nullable = false, length = 64, updatable = false)
    private String issuerId;
    @Column(name = "product_code", nullable = false, length = 64, updatable = false)
    private String productCode;
    @Column(name = "product_version", nullable = false, updatable = false)
    private int productVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 32)
    private CardType cardType;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CardProductStatus status;
    @Column(name = "purchase_enabled", nullable = false)
    private boolean purchaseEnabled;
    @Column(name = "cash_enabled", nullable = false)
    private boolean cashEnabled;
    @Column(name = "ecommerce_enabled", nullable = false)
    private boolean ecommerceEnabled;
    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;
    @Column(name = "creation_idempotency_key", nullable = false, length = 128,
            updatable = false)
    private String creationIdempotencyKey;
    @Column(name = "creation_fingerprint", nullable = false, length = 64,
            updatable = false)
    private String creationFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected CardProduct() {
    }

    public static CardProduct draft(
            String issuerId, String productCode, int productVersion,
            CardType cardType, String currency, boolean purchaseEnabled,
            boolean cashEnabled, boolean ecommerceEnabled, String createdBy,
            String idempotencyKey, String fingerprint) {
        if (blank(issuerId) || blank(productCode) || productVersion < 1
                || cardType == null || currency == null
                || !currency.matches("\\d{3}") || blank(createdBy)
                || blank(idempotencyKey) || blank(fingerprint)) {
            throw new IllegalArgumentException("Invalid card product");
        }
        CardProduct value = new CardProduct();
        value.id = UUID.randomUUID();
        value.issuerId = issuerId;
        value.productCode = productCode;
        value.productVersion = productVersion;
        value.cardType = cardType;
        value.currency = currency;
        value.status = CardProductStatus.DRAFT;
        value.purchaseEnabled = purchaseEnabled;
        value.cashEnabled = cashEnabled;
        value.ecommerceEnabled = ecommerceEnabled;
        value.createdBy = createdBy;
        value.creationIdempotencyKey = idempotencyKey;
        value.creationFingerprint = fingerprint;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public boolean approve(String approver) {
        if (status == CardProductStatus.APPROVED) return false;
        if (status != CardProductStatus.DRAFT) {
            throw new IllegalStateException("Only a draft product can be approved");
        }
        if (createdBy.equals(approver)) {
            throw new IllegalStateException(
                    "Maker and checker must be different for product approval");
        }
        status = CardProductStatus.APPROVED;
        updatedAt = Instant.now();
        return true;
    }

    public boolean activate() {
        if (status == CardProductStatus.ACTIVE) return false;
        if (status != CardProductStatus.APPROVED) {
            throw new IllegalStateException("Only an approved product can be activated");
        }
        status = CardProductStatus.ACTIVE;
        updatedAt = Instant.now();
        return true;
    }

    public boolean creationMatches(String fingerprint) {
        return creationFingerprint.equals(fingerprint);
    }

    public boolean isActive() { return status == CardProductStatus.ACTIVE; }
    public UUID id() { return id; }
    public String issuerId() { return issuerId; }
    public String productCode() { return productCode; }
    public int productVersion() { return productVersion; }
    public CardType cardType() { return cardType; }
    public String currency() { return currency; }
    public CardProductStatus status() { return status; }
    public boolean purchaseEnabled() { return purchaseEnabled; }
    public boolean cashEnabled() { return cashEnabled; }
    public boolean ecommerceEnabled() { return ecommerceEnabled; }
    public String createdBy() { return createdBy; }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
