package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terminal_assignment")
public class TerminalAssignment {
    @Id
    private UUID id;
    @Column(name = "terminal_device_id", nullable = false, updatable = false)
    private UUID terminalDeviceId;
    @Column(name = "outlet_id", nullable = false, updatable = false)
    private UUID outletId;
    @Column(name = "device_contract_id", nullable = false, updatable = false)
    private UUID deviceContractId;
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(nullable = false)
    private boolean active;
    @Version
    private long version;

    protected TerminalAssignment() {}

    public static TerminalAssignment active(UUID terminalDeviceId, UUID outletId,
            UUID deviceContractId) {
        if (terminalDeviceId == null || outletId == null || deviceContractId == null) {
            throw new IllegalArgumentException("Invalid terminal assignment");
        }
        TerminalAssignment value = new TerminalAssignment();
        value.id = UUID.randomUUID();
        value.terminalDeviceId = terminalDeviceId;
        value.outletId = outletId;
        value.deviceContractId = deviceContractId;
        value.assignedAt = Instant.now();
        value.active = true;
        return value;
    }

    public UUID id() { return id; }
    public UUID terminalDeviceId() { return terminalDeviceId; }
    public UUID outletId() { return outletId; }
    public UUID deviceContractId() { return deviceContractId; }
    public boolean isActive() { return active; }
}
