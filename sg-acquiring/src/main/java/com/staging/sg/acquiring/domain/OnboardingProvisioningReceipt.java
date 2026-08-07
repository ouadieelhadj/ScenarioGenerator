package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "acquiring_onboarding_receipt")
public class OnboardingProvisioningReceipt {
    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
    @Column(name = "payload_fingerprint", nullable = false, length = 64, updatable = false)
    private String payloadFingerprint;
    @Column(name = "result_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String resultJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OnboardingProvisioningReceipt() {}

    public static OnboardingProvisioningReceipt completed(String idempotencyKey,
            String payloadFingerprint, String resultJson) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || payloadFingerprint == null || !payloadFingerprint.matches("[0-9a-f]{64}")
                || resultJson == null || resultJson.isBlank()) {
            throw new IllegalArgumentException("Invalid onboarding provisioning receipt");
        }
        OnboardingProvisioningReceipt value = new OnboardingProvisioningReceipt();
        value.idempotencyKey = idempotencyKey;
        value.payloadFingerprint = payloadFingerprint;
        value.resultJson = resultJson;
        value.createdAt = Instant.now();
        return value;
    }

    public boolean matches(String fingerprint) { return payloadFingerprint.equals(fingerprint); }
    public String resultJson() { return resultJson; }
}
