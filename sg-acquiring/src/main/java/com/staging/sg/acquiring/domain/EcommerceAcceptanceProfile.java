package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ecommerce_acceptance_profile", uniqueConstraints =
        @UniqueConstraint(name = "uk_ecommerce_logical_tid", columnNames = {"acquirer_id", "logical_terminal_id"}))
public class EcommerceAcceptanceProfile {
    @Id
    private UUID id;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "logical_terminal_id", nullable = false, length = 8, updatable = false)
    private String logicalTerminalId;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "capture_mode", nullable = false, length = 24)
    private String captureMode;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EcommerceAcceptanceProfile() {}

    public static EcommerceAcceptanceProfile active(String acquirerId, UUID storeId,
            UUID contractId, String logicalTerminalId, String currency,
            String captureMode) {
        if (AcceptanceProduct.blank(acquirerId) || storeId == null || contractId == null
                || logicalTerminalId == null || !logicalTerminalId.matches("[A-Za-z0-9]{8}")
                || !AcceptanceProduct.currency(currency)
                || !("IMMEDIATE".equals(captureMode) || "DEFERRED".equals(captureMode))) {
            throw new IllegalArgumentException("Invalid ecommerce acceptance profile");
        }
        EcommerceAcceptanceProfile value = new EcommerceAcceptanceProfile();
        value.id = UUID.randomUUID();
        value.acquirerId = acquirerId;
        value.storeId = storeId;
        value.contractId = contractId;
        value.logicalTerminalId = logicalTerminalId;
        value.currency = currency;
        value.captureMode = captureMode;
        value.active = true;
        value.createdAt = Instant.now();
        return value;
    }

    public UUID id() { return id; }
    public String acquirerId() { return acquirerId; }
    public UUID storeId() { return storeId; }
    public UUID contractId() { return contractId; }
    public String logicalTerminalId() { return logicalTerminalId; }
    public String currency() { return currency; }
    public String captureMode() { return captureMode; }
    public boolean isActive() { return active; }
}
