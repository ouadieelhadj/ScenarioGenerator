package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_provisioning_job", uniqueConstraints =
        @UniqueConstraint(name = "uk_merchant_provisioning_case", columnNames = "case_id"))
public class ProvisioningJob {
    @Id
    private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;
    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true, updatable = false)
    private String idempotencyKey;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProvisioningJobStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected ProvisioningJob() {}

    public static ProvisioningJob pending(UUID caseId, String idempotencyKey, String payloadJson) {
        ProvisioningJob value = new ProvisioningJob();
        value.id = UUID.randomUUID();
        value.caseId = caseId;
        value.idempotencyKey = idempotencyKey;
        value.payloadJson = payloadJson;
        value.status = ProvisioningJobStatus.PENDING;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void processing() {
        if (status != ProvisioningJobStatus.PENDING && status != ProvisioningJobStatus.FAILED)
            throw new IllegalStateException("Job is not processable");
        status = ProvisioningJobStatus.PROCESSING;
        attempts++;
        updatedAt = Instant.now();
    }
    public void succeeded() { status = ProvisioningJobStatus.SUCCEEDED; lastError = null; updatedAt = Instant.now(); }
    public void failed(String error) { status = ProvisioningJobStatus.FAILED; lastError = truncate(error); updatedAt = Instant.now(); }
    private static String truncate(String value) {
        if (value == null) return "Unknown provisioning error";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String payloadJson() { return payloadJson; }
    public ProvisioningJobStatus status() { return status; }
    public int attempts() { return attempts; }
    public String lastError() { return lastError; }
}
