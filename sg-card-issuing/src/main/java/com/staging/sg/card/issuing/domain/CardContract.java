package com.staging.sg.card.issuing.domain;

import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
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
@Table(name = "payment_contract",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_contract_external",
                        columnNames = {"institution_id", "external_reference"}),
                @UniqueConstraint(name = "uk_payment_contract_idempotency",
                        columnNames = {"institution_id", "created_by", "creation_idempotency_key"})
        })
public class CardContract {
    @Id
    private UUID id;
    @Column(name = "institution_id", nullable = false, length = 64, updatable = false)
    private String issuerId;
    @Column(name = "external_reference", nullable = false, length = 128, updatable = false)
    private String externalReference;
    @Column(name = "customer_id", nullable = false, length = 128, updatable = false)
    private String customerId;
    @Column(name = "beneficiary_id", nullable = false, length = 128, updatable = false)
    private String cardholderId;
    @Column(name = "funding_contract_id", nullable = false, length = 128, updatable = false)
    private String fundingContractId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 32, updatable = false)
    private PaymentContractType contractType;
    @Column(name = "parent_contract_id", updatable = false)
    private UUID parentContractId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentContractStatus status;
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
        value.contractType = PaymentContractType.ISSUING_CARD;
        value.status = PaymentContractStatus.DRAFT;
        value.createdBy = createdBy;
        value.creationIdempotencyKey = idempotencyKey;
        value.creationFingerprint = fingerprint;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public boolean submit() {
        if (status == PaymentContractStatus.PENDING_APPROVAL) return false;
        require(PaymentContractStatus.DRAFT, "Only a draft contract can be submitted");
        status = PaymentContractStatus.PENDING_APPROVAL;
        updatedAt = Instant.now();
        return true;
    }

    public boolean approve(String approver) {
        if (status == PaymentContractStatus.ACTIVE) return false;
        require(PaymentContractStatus.PENDING_APPROVAL,
                "Only a pending contract can be approved");
        if (createdBy.equals(approver)) {
            throw new IllegalStateException(
                    "Maker and checker must be different for contract approval");
        }
        status = PaymentContractStatus.ACTIVE;
        updatedAt = Instant.now();
        return true;
    }

    public void suspend() {
        if (status == PaymentContractStatus.SUSPENDED) return;
        require(PaymentContractStatus.ACTIVE, "Only an active contract can be suspended");
        status = PaymentContractStatus.SUSPENDED;
        updatedAt = Instant.now();
    }

    public void reactivate() {
        if (status == PaymentContractStatus.ACTIVE) return;
        require(PaymentContractStatus.SUSPENDED,
                "Only a suspended contract can be reactivated");
        status = PaymentContractStatus.ACTIVE;
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
    public PaymentContractType contractType() { return contractType; }
    public UUID parentContractId() { return parentContractId; }
    public PaymentContractStatus status() { return status; }
    public String createdBy() { return createdBy; }

    private void require(PaymentContractStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
