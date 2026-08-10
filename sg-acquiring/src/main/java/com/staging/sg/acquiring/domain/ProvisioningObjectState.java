package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provisioning_object_state", uniqueConstraints =
        @UniqueConstraint(name = "uk_provisioning_object_key", columnNames = "idempotency_key"))
public class ProvisioningObjectState {
    @Id private UUID id;
    @Column(name = "object_type", nullable = false, length = 48) private String objectType;
    @Column(name = "object_id", nullable = false) private UUID objectId;
    @Column(name = "idempotency_key", nullable = false, length = 200) private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private ProvisioningObjectStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "external_reference", length = 128) private String externalReference;
    @Column(name = "allocated_identifier", length = 32) private String allocatedIdentifier;
    @Column(name = "last_error_code", length = 64) private String lastErrorCode;
    @Column(name = "last_error_message", length = 500) private String lastErrorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected ProvisioningObjectState() {}

    public static ProvisioningObjectState pending(String objectType, UUID objectId,
            String idempotencyKey, String payloadHash) {
        if (AcceptanceProduct.blank(objectType) || objectId == null
                || AcceptanceProduct.blank(idempotencyKey) || idempotencyKey.length() > 200
                || payloadHash == null || !payloadHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("PROV-002: invalid provisioning object state");
        ProvisioningObjectState value = new ProvisioningObjectState();
        value.id = UUID.randomUUID(); value.objectType = objectType; value.objectId = objectId;
        value.idempotencyKey = idempotencyKey; value.payloadHash = payloadHash;
        value.status = ProvisioningObjectStatus.PENDING; value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void requirePayload(String hash) {
        if (!payloadHash.equals(hash))
            throw new IllegalStateException("PROV-002: idempotency key reused with another payload");
    }
    public void start() { status = ProvisioningObjectStatus.IN_PROGRESS; attemptCount++; updatedAt = Instant.now(); }
    public void allocate(String identifier) {
        if (AcceptanceProduct.blank(identifier)) throw new IllegalArgumentException("identifier is required");
        if (allocatedIdentifier != null && !allocatedIdentifier.equals(identifier))
            throw new IllegalStateException("PROV-002: allocated identifier cannot change");
        allocatedIdentifier = identifier; updatedAt = Instant.now();
    }
    public void provisioned(String reference) {
        externalReference = reference; status = ProvisioningObjectStatus.PROVISIONED;
        nextAttemptAt = null; lastErrorCode = null; lastErrorMessage = null; updatedAt = Instant.now();
    }
    public void failed(String code, String message, boolean retryable, Instant nextAttempt) {
        lastErrorCode = safe(code, 64); lastErrorMessage = safe(message, 500);
        status = retryable && attemptCount < 8 ? ProvisioningObjectStatus.FAILED_RETRYABLE
                : ProvisioningObjectStatus.FAILED_FINAL;
        nextAttemptAt = status == ProvisioningObjectStatus.FAILED_RETRYABLE ? nextAttempt : null;
        updatedAt = Instant.now();
    }
    private static String safe(String value, int max) {
        if (value == null) return null;
        String sanitized = value.replaceAll("(?i)(bearer|token|password|secret)\\s*[:=]\\s*\\S+", "$1=***");
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }
    public UUID id() { return id; }
    public UUID objectId() { return objectId; }
    public String idempotencyKey() { return idempotencyKey; }
    public ProvisioningObjectStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public String externalReference() { return externalReference; }
    public String allocatedIdentifier() { return allocatedIdentifier; }
    public String lastErrorCode() { return lastErrorCode; }
    public String lastErrorMessage() { return lastErrorMessage; }
}
