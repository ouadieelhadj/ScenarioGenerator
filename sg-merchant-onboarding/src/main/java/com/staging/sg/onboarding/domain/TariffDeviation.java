package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_tariff_deviation")
public class TariffDeviation {
    @Id private UUID id;
    @Column(name = "outlet_product_id", nullable = false, updatable = false) private UUID outletProductId;
    @Column(name = "pack_code", nullable = false, length = 64, updatable = false) private String packCode;
    @Column(name = "pack_version", nullable = false, updatable = false) private int packVersion;
    @Column(name = "before_json", nullable = false, columnDefinition = "TEXT", updatable = false) private String beforeJson;
    @Column(name = "after_json", nullable = false, columnDefinition = "TEXT", updatable = false) private String afterJson;
    @Column(nullable = false, length = 1000, updatable = false) private String reason;
    @Column(name = "requested_by", nullable = false, length = 96, updatable = false) private String requestedBy;
    @Column(name = "requested_at", nullable = false, updatable = false) private Instant requestedAt;
    @Column(name = "decided_by", length = 96) private String decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TariffDeviationStatus status;
    @Version private long version;
    protected TariffDeviation() {}

    public static TariffDeviation request(UUID outletProductId, String packCode, int packVersion,
            String beforeJson, String afterJson, String reason, String actor) {
        if (outletProductId == null || packVersion < 1 || blank(packCode) || blank(beforeJson)
                || blank(afterJson) || blank(reason) || blank(actor))
            throw new IllegalArgumentException("TAR-003: incomplete tariff deviation");
        TariffDeviation value = new TariffDeviation(); value.id = UUID.randomUUID();
        value.outletProductId = outletProductId; value.packCode = packCode;
        value.packVersion = packVersion; value.beforeJson = beforeJson; value.afterJson = afterJson;
        value.reason = trim(reason, 1000); value.requestedBy = trim(actor, 96);
        value.requestedAt = Instant.now(); value.status = TariffDeviationStatus.PENDING_APPROVAL;
        return value;
    }
    public void approve(String actor) { decide(actor, null, TariffDeviationStatus.APPROVED); }
    public void reject(String actor, String reason) { if (blank(reason)) throw new IllegalArgumentException("Decision reason is required");
        decide(actor, trim(reason, 1000), TariffDeviationStatus.REJECTED); }
    private void decide(String actor, String reason, TariffDeviationStatus target) {
        if (status != TariffDeviationStatus.PENDING_APPROVAL) throw new IllegalStateException("Deviation is already decided");
        if (blank(actor) || requestedBy.equals(actor.trim())) throw new IllegalStateException("Maker and checker must be different");
        decidedBy = trim(actor, 96); decisionReason = reason; decidedAt = Instant.now(); status = target;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value, int max) { String result = value.trim(); if (result.length() > max)
        throw new IllegalArgumentException("Value is too long"); return result; }
    public UUID id() { return id; } public UUID outletProductId() { return outletProductId; }
    public String packCode() { return packCode; } public int packVersion() { return packVersion; }
    public String beforeJson() { return beforeJson; } public String afterJson() { return afterJson; }
    public String reason() { return reason; } public String requestedBy() { return requestedBy; }
    public TariffDeviationStatus status() { return status; } public long version() { return version; }
}
