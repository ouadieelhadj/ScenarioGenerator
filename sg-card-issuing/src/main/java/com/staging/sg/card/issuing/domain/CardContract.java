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
@Table(name = "issuing_card_contract",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_issuing_contract_external",
                        columnNames = {"issuer_id", "external_reference"}),
                @UniqueConstraint(name = "uk_issuing_contract_idempotency",
                        columnNames = {"issuer_id", "created_by", "creation_idempotency_key"})
        })
public class CardContract {
    @Id
    private UUID id;
    @Column(name = "issuer_id", nullable = false, length = 64, updatable = false)
    private String issuerId;
    @Column(name = "external_reference", nullable = false, length = 128, updatable = false)
    private String externalReference;
    @Column(name = "customer_id", nullable = false, length = 128, updatable = false)
    private String customerId;
    @Column(name = "cardholder_id", nullable = false, length = 128, updatable = false)
    private String cardholderId;
    @Column(name = "funding_contract_id", nullable = false, length = 128, updatable = false)
    private String fundingContractId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CardContractStatus status;
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

    protected CardContract() {
    }

    public static CardContract draft(
            String issuerId, String externalReference, String customerId,
            String cardholderId, String fundingContractId, UUID productId,
            String createdBy, String idempotencyKey, String fingerprint) {
        if (blank(issuerId) || blank(externalReference) || blank(customerId)
                || blank(cardholderId) || blank(fundingContractId)
                || productId == null || blank(createdBy)
                || blank(idempotencyKey) || blank(fingerprint)) {
            throw new IllegalArgumentException("Invalid card contract");
        }
        CardContract value = new CardContract();
        value.id = UUID.randomUUID();
        value.issuerId = issuerId;
        value.externalReference = externalReference;
        value.customerId = customerId;
        value.cardholderId = cardholderId;
        value.fundingContractId = fundingContractId;
        value.productId = productId;
        value.status = CardContractStatus.DRAFT;
        value.createdBy = createdBy;
        value.creationIdempotencyKey = idempotencyKey;
        value.creationFingerprint = fingerprint;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public boolean submit() {
        if (status == CardContractStatus.PENDING_APPROVAL) return false;
        require(CardContractStatus.DRAFT, "Only a draft contract can be submitted");
        status = CardContractStatus.PENDING_APPROVAL;
        updatedAt = Instant.now();
        return true;
    }

    public boolean approve(String approver) {
        if (status == CardContractStatus.ACTIVE) return false;
        require(CardContractStatus.PENDING_APPROVAL,
                "Only a pending contract can be approved");
        if (createdBy.equals(approver)) {
            throw new IllegalStateException(
                    "Maker and checker must be different for contract approval");
        }
        status = CardContractStatus.ACTIVE;
        updatedAt = Instant.now();
        return true;
    }

    public void suspend() {
        if (status == CardContractStatus.SUSPENDED) return;
        require(CardContractStatus.ACTIVE, "Only an active contract can be suspended");
        status = CardContractStatus.SUSPENDED;
        updatedAt = Instant.now();
    }

    public void reactivate() {
        if (status == CardContractStatus.ACTIVE) return;
        require(CardContractStatus.SUSPENDED,
                "Only a suspended contract can be reactivated");
        status = CardContractStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public boolean creationMatches(String fingerprint) {
        return creationFingerprint.equals(fingerprint);
    }

    public UUID id() { return id; }
    public String issuerId() { return issuerId; }
    public String externalReference() { return externalReference; }
    public String customerId() { return customerId; }
    public String cardholderId() { return cardholderId; }
    public String fundingContractId() { return fundingContractId; }
    public UUID productId() { return productId; }
    public CardContractStatus status() { return status; }
    public String createdBy() { return createdBy; }

    private void require(CardContractStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
