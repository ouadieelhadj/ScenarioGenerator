package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_onboarding_outbox_idempotency", columnNames = "idempotency_key"))
public class OnboardingOutboxEvent {
    public static final int MAX_ATTEMPTS = 8;

    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 96, updatable = false) private String eventType;
    @Column(name = "schema_version", nullable = false, length = 16, updatable = false) private String schemaVersion;
    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false) private String idempotencyKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false) private String payloadJson;
    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false) private String payloadHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private OnboardingOutboxStatus status;
    @Column(name = "attempt_count", nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "locked_by", length = 96) private String lockedBy;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "last_error_code", length = 64) private String lastErrorCode;
    @Column(name = "last_error_message", length = 1000) private String lastErrorMessage;
    @Column(name = "last_correlation_id", length = 96) private String lastCorrelationId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected OnboardingOutboxEvent() {}

    public static OnboardingOutboxEvent provisioningRequested(UUID caseId,
            String payloadJson, String payloadHash) {
        return requested(caseId,"merchant.provisioning.requested","2.0",
                "merchant-onboarding-v2:"+caseId,payloadJson,payloadHash);
    }

    public static OnboardingOutboxEvent way4ExportRequested(UUID caseId,
            String payloadJson, String payloadHash) {
        return requested(caseId,"way4.export.requested","2.0",
                "merchant-way4-v2:"+caseId,payloadJson,payloadHash);
    }

    private static OnboardingOutboxEvent requested(UUID caseId,String eventType,
            String schemaVersion,String idempotencyKey,String payloadJson,String payloadHash) {
        OnboardingOutboxEvent value = new OnboardingOutboxEvent();
        value.id = UUID.randomUUID();
        value.aggregateType = "MERCHANT_ONBOARDING_CASE";
        value.aggregateId = caseId;
        value.eventType = eventType;
        value.schemaVersion = schemaVersion;
        value.idempotencyKey = idempotencyKey;
        value.payloadJson = payloadJson;
        value.payloadHash = payloadHash;
        value.status = OnboardingOutboxStatus.PENDING;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        value.availableAt = value.createdAt;
        return value;
    }

    public void reserve(String workerId, String correlationId, Instant now) {
        boolean expiredLease = status == OnboardingOutboxStatus.PROCESSING
                && leaseUntil != null && !leaseUntil.isAfter(now);
        if ((status != OnboardingOutboxStatus.PENDING || availableAt.isAfter(now)) && !expiredLease)
            throw new IllegalStateException("Outbox event is not reservable");
        if (attempts >= MAX_ATTEMPTS)
            throw new IllegalStateException("Outbox automatic retry budget is exhausted");
        status = OnboardingOutboxStatus.PROCESSING;
        attempts++;
        lockedBy = required(workerId, "workerId", 96);
        lockedAt = now;
        leaseUntil = now.plusSeconds(120);
        lastCorrelationId = correlationId;
        updatedAt = now;
    }

    public void complete() {
        status = OnboardingOutboxStatus.COMPLETED;
        processedAt = Instant.now();
        clearLock();
        lastErrorCode = null;
        lastErrorMessage = null;
        updatedAt = processedAt;
    }

    public void fail(String code, String message, boolean retryable) {
        lastErrorCode = truncate(code, 64);
        lastErrorMessage = truncate(message, 1000);
        Instant now = Instant.now();
        updatedAt = now;
        clearLock();
        if (!retryable || attempts >= MAX_ATTEMPTS) {
            status = OnboardingOutboxStatus.FAILED_FINAL;
            processedAt = now;
            availableAt = now;
            return;
        }
        status = OnboardingOutboxStatus.PENDING;
        long baseSeconds = Math.min(1800L, 30L * (1L << Math.min(attempts - 1, 6)));
        long spread = Math.max(1L, baseSeconds / 5L);
        long offset = Math.floorMod(id.getLeastSignificantBits() + attempts,
                spread * 2L + 1L) - spread;
        availableAt = now.plusSeconds(Math.max(1L, baseSeconds + offset));
    }

    public void manualRetry(String actor, String reason) {
        if (status != OnboardingOutboxStatus.FAILED_FINAL)
            throw new IllegalStateException("Only a final failed event can be retried manually");
        required(actor, "actor", 96);
        required(reason, "reason", 1000);
        status = OnboardingOutboxStatus.PENDING;
        attempts = 0;
        availableAt = Instant.now();
        processedAt = null;
        clearLock();
        lastErrorCode = "MANUAL_RETRY";
        lastErrorMessage = truncate(reason, 1000);
        updatedAt = availableAt;
    }

    private void clearLock() { lockedBy = null; lockedAt = null; leaseUntil = null; }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }
    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    public UUID id() { return id; }
    public UUID aggregateId() { return aggregateId; }
    public String eventType() { return eventType; }
    public String idempotencyKey() { return idempotencyKey; }
    public String payloadJson() { return payloadJson; }
    public String payloadHash() { return payloadHash; }
    public OnboardingOutboxStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant availableAt() { return availableAt; }
    public String lastCorrelationId() { return lastCorrelationId; }
}
