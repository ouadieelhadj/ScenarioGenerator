package com.staging.sg.acquiring.domain;

import com.staging.sg.common.contract.PaymentContractStatus;
import com.staging.sg.common.contract.PaymentContractType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_contract", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_contract_external",
                columnNames = {"institution_id", "external_reference"}),
        @UniqueConstraint(name = "uk_payment_contract_idempotency",
                columnNames = {"institution_id", "created_by", "creation_idempotency_key"})
})
public class AcquiringContract {
    @Id
    private UUID id;
    @Column(name = "institution_id", nullable = false, length = 64, updatable = false)
    private String institutionId;
    @Column(name = "external_reference", nullable = false, length = 128, updatable = false)
    private String externalReference;
    @Column(name = "customer_id", nullable = false, length = 128, updatable = false)
    private String customerId;
    @Column(name = "beneficiary_id", nullable = false, length = 128, updatable = false)
    private String beneficiaryId;
    @Column(name = "funding_contract_id", length = 128, updatable = false)
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
    @Column(name = "creation_idempotency_key", nullable = false, length = 128, updatable = false)
    private String creationIdempotencyKey;
    @Column(name = "creation_fingerprint", nullable = false, length = 64, updatable = false)
    private String creationFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected AcquiringContract() {}

    public static AcquiringContract merchant(String institutionId,
            String externalReference, UUID merchantId, String settlementAccountReference,
            UUID productId, String createdBy, String idempotencyKey, String fingerprint) {
        return draft(institutionId, externalReference, merchantId, settlementAccountReference,
                productId, PaymentContractType.ACQUIRING_MERCHANT, null,
                createdBy, idempotencyKey, fingerprint);
    }

    public static AcquiringContract device(String institutionId,
            String externalReference, UUID merchantId, UUID parentContractId,
            UUID productId, String createdBy, String idempotencyKey, String fingerprint) {
        if (parentContractId == null) {
            throw new IllegalArgumentException("A device contract requires a parent contract");
        }
        return draft(institutionId, externalReference, merchantId, null, productId,
                PaymentContractType.ACQUIRING_DEVICE, parentContractId,
                createdBy, idempotencyKey, fingerprint);
    }

    private static AcquiringContract draft(String institutionId,
            String externalReference, UUID merchantId, String fundingReference,
            UUID productId, PaymentContractType type, UUID parentContractId,
            String createdBy, String idempotencyKey, String fingerprint) {
        if (AcceptanceProduct.blank(institutionId)
                || AcceptanceProduct.blank(externalReference) || merchantId == null
                || productId == null || type == null
                || AcceptanceProduct.blank(createdBy)
                || AcceptanceProduct.blank(idempotencyKey)
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid acquiring contract");
        }
        AcquiringContract value = new AcquiringContract();
        value.id = UUID.randomUUID();
        value.institutionId = institutionId;
        value.externalReference = externalReference;
        value.customerId = merchantId.toString();
        value.beneficiaryId = merchantId.toString();
        value.fundingContractId = fundingReference;
        value.productId = productId;
        value.contractType = type;
        value.parentContractId = parentContractId;
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

    public boolean approve(String checker) {
        if (status == PaymentContractStatus.ACTIVE) return false;
        require(PaymentContractStatus.PENDING_APPROVAL,
                "Only a pending contract can be approved");
        if (createdBy.equals(checker)) {
            throw new IllegalStateException("Maker and checker must be different");
        }
        status = PaymentContractStatus.ACTIVE;
        updatedAt = Instant.now();
        return true;
    }

    public void suspend() {
        require(PaymentContractStatus.ACTIVE, "Only an active contract can be suspended");
        status = PaymentContractStatus.SUSPENDED;
        updatedAt = Instant.now();
    }

    public boolean creationMatches(String fingerprint) { return creationFingerprint.equals(fingerprint); }
    public UUID id() { return id; }
    public String institutionId() { return institutionId; }
    public String externalReference() { return externalReference; }
    public UUID merchantId() { return UUID.fromString(customerId); }
    public String fundingContractId() { return fundingContractId; }
    public UUID productId() { return productId; }
    public PaymentContractType contractType() { return contractType; }
    public UUID parentContractId() { return parentContractId; }
    public PaymentContractStatus status() { return status; }
    public String createdBy() { return createdBy; }

    private void require(PaymentContractStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }
}
