package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_reference_audit")
public class OnboardingReferenceAudit {
    @Id private UUID id;
    @Column(nullable = false, length = 32, updatable = false) private String category;
    @Column(nullable = false, length = 64, updatable = false) private String code;
    @Column(nullable = false, length = 24, updatable = false) private String action;
    @Column(name = "before_json", columnDefinition = "TEXT", updatable = false) private String beforeJson;
    @Column(name = "after_json", columnDefinition = "TEXT", updatable = false) private String afterJson;
    @Column(name = "changed_by", nullable = false, length = 96, updatable = false) private String changedBy;
    @Column(name = "changed_at", nullable = false, updatable = false) private Instant changedAt;

    protected OnboardingReferenceAudit() {}

    public static OnboardingReferenceAudit create(String category, String code, String action,
            String beforeJson, String afterJson, String actor) {
        OnboardingReferenceAudit value = new OnboardingReferenceAudit();
        value.id = UUID.randomUUID(); value.category = category; value.code = code;
        value.action = action; value.beforeJson = beforeJson; value.afterJson = afterJson;
        value.changedBy = actor; value.changedAt = Instant.now();
        return value;
    }
}
