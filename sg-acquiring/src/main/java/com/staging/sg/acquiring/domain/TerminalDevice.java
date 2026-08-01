package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terminal_device", uniqueConstraints =
        @UniqueConstraint(name = "uk_terminal_serial", columnNames = {"acquirer_id", "serial_number"}))
public class TerminalDevice {
    @Id
    private UUID id;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "serial_number", nullable = false, length = 96, updatable = false)
    private String serialNumber;
    @Column(name = "model_code", nullable = false, length = 64, updatable = false)
    private String modelCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TerminalStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected TerminalDevice() {}

    public static TerminalDevice inStock(String acquirerId, String serialNumber,
            String modelCode) {
        if (AcceptanceProduct.blank(acquirerId) || AcceptanceProduct.blank(serialNumber)
                || AcceptanceProduct.blank(modelCode)) {
            throw new IllegalArgumentException("Invalid terminal device");
        }
        TerminalDevice value = new TerminalDevice();
        value.id = UUID.randomUUID();
        value.acquirerId = acquirerId;
        value.serialNumber = serialNumber;
        value.modelCode = modelCode;
        value.status = TerminalStatus.IN_STOCK;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void assign() {
        require(TerminalStatus.IN_STOCK, "Only an in-stock terminal can be assigned");
        status = TerminalStatus.ASSIGNED;
        updatedAt = Instant.now();
    }

    public void provisioning() {
        if (status == TerminalStatus.PROVISIONING) return;
        require(TerminalStatus.ASSIGNED, "Only an assigned terminal can be provisioned");
        status = TerminalStatus.PROVISIONING;
        updatedAt = Instant.now();
    }

    public void ready() {
        require(TerminalStatus.PROVISIONING, "Only a provisioning terminal can become ready");
        status = TerminalStatus.READY;
        updatedAt = Instant.now();
    }

    public void activate() {
        require(TerminalStatus.READY, "Only a ready terminal can be activated");
        status = TerminalStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String acquirerId() { return acquirerId; }
    public String serialNumber() { return serialNumber; }
    public String modelCode() { return modelCode; }
    public TerminalStatus status() { return status; }

    private void require(TerminalStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }
}
