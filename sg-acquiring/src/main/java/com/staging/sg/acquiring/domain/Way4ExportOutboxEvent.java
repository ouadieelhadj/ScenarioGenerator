package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acquiring_way4_export_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_acquiring_way4_export_key", columnNames = "idempotency_key"))
public class Way4ExportOutboxEvent {
    @Id private UUID id;
    @Column(name = "onboarding_case_id", nullable = false, updatable = false) private UUID onboardingCaseId;
    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false) private String idempotencyKey;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT", updatable = false) private String payloadJson;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Way4ExportOutboxStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "locked_by", length = 96) private String lockedBy;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "way4_file_id") private UUID way4FileId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected Way4ExportOutboxEvent() {}
    public static Way4ExportOutboxEvent pending(UUID caseId, String key, String json, String hash) {
        return pending(caseId, key, json, hash, Instant.now());
    }
    static Way4ExportOutboxEvent pending(UUID caseId, String key, String json, String hash, Instant now) {
        Way4ExportOutboxEvent value = new Way4ExportOutboxEvent(); value.id = UUID.randomUUID();
        value.onboardingCaseId = caseId; value.idempotencyKey = key; value.payloadJson = json;
        value.payloadHash = hash; value.status = Way4ExportOutboxStatus.PENDING;
        value.createdAt = now; value.updatedAt = now; value.availableAt = now;
        return value;
    }
    public UUID id() { return id; } public String idempotencyKey() { return idempotencyKey; }
    public String payloadJson() { return payloadJson; } public String payloadHash() { return payloadHash; }
    public Way4ExportOutboxStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant availableAt() { return availableAt; }
    public void reserve(String workerId, Instant now, Instant leaseEnd) {
        if (status != Way4ExportOutboxStatus.PENDING || availableAt.isAfter(now) || attempts >= 8
                || workerId == null || workerId.isBlank() || leaseEnd == null || !leaseEnd.isAfter(now))
            throw new IllegalStateException("WAY4 export event is not dispatchable");
        status = Way4ExportOutboxStatus.PROCESSING; attempts++; updatedAt = now;
        lockedBy = workerId; lockedAt = now; leaseUntil = leaseEnd;
    }
    public void completed(String workerId, UUID fileId, Instant now) {
        if (status != Way4ExportOutboxStatus.PROCESSING || !ownsLease(workerId) || fileId == null)
            throw new IllegalStateException("WAY4 export event is not processing");
        status = Way4ExportOutboxStatus.COMPLETED; way4FileId = fileId; lastError = null; updatedAt = now;
        clearLease();
    }
    public void failed(String workerId, String error, boolean retryable, boolean mappingBlocked, Instant now) {
        if (status != Way4ExportOutboxStatus.PROCESSING || !ownsLease(workerId))
            throw new IllegalStateException("WAY4 export event is not processing");
        lastError = clean(error); updatedAt = now;
        if (mappingBlocked) { status = Way4ExportOutboxStatus.MAPPING_BLOCKED; clearLease(); return; }
        if (!retryable || attempts >= 8) { status = Way4ExportOutboxStatus.FAILED_FINAL; clearLease(); return; }
        status = Way4ExportOutboxStatus.PENDING;
        long delay = Math.min(1800L, 30L * (1L << Math.min(6, attempts - 1)));
        availableAt = updatedAt.plusSeconds(delay);
        clearLease();
    }
    private boolean ownsLease(String workerId) { return workerId != null && workerId.equals(lockedBy); }
    private void clearLease() { lockedBy = null; lockedAt = null; leaseUntil = null; }
    private static String clean(String value) { if (value == null) return "Unknown connector error";
        String result = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return result.length() <= 1000 ? result : result.substring(0, 1000); }
}
