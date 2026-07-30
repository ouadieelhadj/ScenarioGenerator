package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pos_holds")
public class PosHold {
    @Id
    @Column(name = "transaction_id", length = 64)
    private String transactionId;
    @Column(name = "pan_hash", nullable = false, length = 64)
    private String panHash;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PosHold() {
    }

    public static PosHold active(String transactionId, String panHash, long amount) {
        PosHold hold = new PosHold();
        hold.transactionId = transactionId;
        hold.panHash = panHash;
        hold.amountMinor = amount;
        hold.status = "ACTIVE";
        hold.createdAt = Instant.now();
        hold.updatedAt = hold.createdAt;
        return hold;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public long getAmountMinor() { return amountMinor; }
    public String getPanHash() { return panHash; }

    public void release() {
        status = "RELEASED";
        updatedAt = Instant.now();
    }

    public void capture() {
        status = "CAPTURED";
        updatedAt = Instant.now();
    }
}
