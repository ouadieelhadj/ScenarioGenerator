package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acceptance_product_version", uniqueConstraints =
        @UniqueConstraint(name = "uk_acceptance_product_version",
                columnNames = {"acquirer_id", "product_code", "product_version"}))
public class AcceptanceProduct {
    @Id
    private UUID id;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "product_code", nullable = false, length = 64, updatable = false)
    private String productCode;
    @Column(name = "product_version", nullable = false, updatable = false)
    private int productVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AcceptanceChannel channel;
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ApprovalStatus status;
    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected AcceptanceProduct() {}

    public static AcceptanceProduct draft(String acquirerId, String productCode,
            int productVersion, AcceptanceChannel channel, String currency,
            String createdBy) {
        if (blank(acquirerId) || blank(productCode) || productVersion < 1
                || channel == null || !currency(currency) || blank(createdBy)) {
            throw new IllegalArgumentException("Invalid acceptance product");
        }
        AcceptanceProduct value = new AcceptanceProduct();
        value.id = UUID.randomUUID();
        value.acquirerId = acquirerId;
        value.productCode = productCode;
        value.productVersion = productVersion;
        value.channel = channel;
        value.defaultCurrency = currency;
        value.status = ApprovalStatus.DRAFT;
        value.createdBy = createdBy;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void submit() {
        require(ApprovalStatus.DRAFT, "Only a draft product can be submitted");
        status = ApprovalStatus.PENDING_APPROVAL;
        updatedAt = Instant.now();
    }

    public void approve(String checker) {
        require(ApprovalStatus.PENDING_APPROVAL, "Only a pending product can be approved");
        if (createdBy.equals(checker)) {
            throw new IllegalStateException("Maker and checker must be different");
        }
        status = ApprovalStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String acquirerId() { return acquirerId; }
    public String productCode() { return productCode; }
    public int productVersion() { return productVersion; }
    public AcceptanceChannel channel() { return channel; }
    public String defaultCurrency() { return defaultCurrency; }
    public ApprovalStatus status() { return status; }
    public boolean isActive() { return status == ApprovalStatus.ACTIVE; }

    private void require(ApprovalStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    static boolean blank(String value) { return value == null || value.isBlank(); }
    static boolean currency(String value) { return value != null && value.matches("\\d{3}"); }
}
