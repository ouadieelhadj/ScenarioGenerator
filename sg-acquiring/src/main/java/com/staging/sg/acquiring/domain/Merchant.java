package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant", uniqueConstraints = {
        @UniqueConstraint(name = "uk_merchant_registration",
                columnNames = {"acquirer_id", "registration_number"}),
        @UniqueConstraint(name = "uk_merchant_idempotency",
                columnNames = {"acquirer_id", "created_by", "creation_idempotency_key"})
})
public class Merchant {
    @Id
    private UUID id;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "legal_name", nullable = false, length = 160)
    private String legalName;
    @Column(name = "trading_name", nullable = false, length = 160)
    private String tradingName;
    @Column(name = "registration_number", nullable = false, length = 64, updatable = false)
    private String registrationNumber;
    @Column(nullable = false, length = 2)
    private String country;
    @Column(nullable = false, length = 4)
    private String mcc;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ApprovalStatus status;
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

    protected Merchant() {}

    public static Merchant draft(String acquirerId, String legalName,
            String tradingName, String registrationNumber, String country,
            String mcc, String createdBy, String idempotencyKey,
            String fingerprint) {
        if (AcceptanceProduct.blank(acquirerId) || AcceptanceProduct.blank(legalName)
                || AcceptanceProduct.blank(tradingName)
                || AcceptanceProduct.blank(registrationNumber)
                || country == null || !country.matches("[A-Z]{2}")
                || mcc == null || !mcc.matches("\\d{4}")
                || AcceptanceProduct.blank(createdBy)
                || AcceptanceProduct.blank(idempotencyKey)
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid merchant");
        }
        Merchant value = new Merchant();
        value.id = UUID.randomUUID();
        value.acquirerId = acquirerId;
        value.legalName = legalName;
        value.tradingName = tradingName;
        value.registrationNumber = registrationNumber;
        value.country = country;
        value.mcc = mcc;
        value.status = ApprovalStatus.DRAFT;
        value.createdBy = createdBy;
        value.creationIdempotencyKey = idempotencyKey;
        value.creationFingerprint = fingerprint;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public boolean submit() {
        if (status == ApprovalStatus.PENDING_APPROVAL) return false;
        require(ApprovalStatus.DRAFT, "Only a draft merchant can be submitted");
        status = ApprovalStatus.PENDING_APPROVAL;
        updatedAt = Instant.now();
        return true;
    }

    public boolean approve(String checker) {
        if (status == ApprovalStatus.ACTIVE) return false;
        require(ApprovalStatus.PENDING_APPROVAL, "Only a pending merchant can be approved");
        if (createdBy.equals(checker)) {
            throw new IllegalStateException("Maker and checker must be different");
        }
        status = ApprovalStatus.ACTIVE;
        updatedAt = Instant.now();
        return true;
    }

    public boolean creationMatches(String fingerprint) { return creationFingerprint.equals(fingerprint); }
    public UUID id() { return id; }
    public String acquirerId() { return acquirerId; }
    public String legalName() { return legalName; }
    public String tradingName() { return tradingName; }
    public String registrationNumber() { return registrationNumber; }
    public String country() { return country; }
    public String mcc() { return mcc; }
    public ApprovalStatus status() { return status; }
    public boolean isActive() { return status == ApprovalStatus.ACTIVE; }
    public String createdBy() { return createdBy; }

    private void require(ApprovalStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }
}
