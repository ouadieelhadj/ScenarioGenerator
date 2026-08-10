package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "onboarding_reference_value")
public class OnboardingReferenceValue {
    @EmbeddedId private OnboardingReferenceValueId id;
    @Column(nullable = false, length = 160) private String label;
    @Column(nullable = false) private boolean active;
    @Column(name = "attributes_json") private String attributesJson;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "updated_by", nullable = false, length = 96) private String updatedBy;
    @Version private long version;

    protected OnboardingReferenceValue() {}

    public static OnboardingReferenceValue active(String category, String code, String label) {
        return active(category, code, label, null, "SYSTEM");
    }

    public static OnboardingReferenceValue active(String category, String code, String label,
            String attributesJson, String actor) {
        OnboardingReferenceValue value = new OnboardingReferenceValue();
        value.id = new OnboardingReferenceValueId(required(category, "category", 32),
                required(code, "code", 64));
        value.label = required(label, "label", 160);
        value.active = true;
        value.attributesJson = optional(attributesJson, 16000);
        value.validFrom = Instant.now();
        value.updatedAt = value.validFrom;
        value.updatedBy = required(actor, "actor", 96);
        return value;
    }

    public void update(String label, String attributesJson, String actor) {
        this.label = required(label, "label", 160);
        this.attributesJson = optional(attributesJson, 16000);
        this.updatedBy = required(actor, "actor", 96);
        this.updatedAt = Instant.now();
    }

    public void activate(String actor) {
        if (active) throw new IllegalStateException("Reference value is already active");
        active = true;
        validFrom = Instant.now();
        validTo = null;
        updatedBy = required(actor, "actor", 96);
        updatedAt = validFrom;
    }

    public void deactivate(String actor) {
        if (!active) throw new IllegalStateException("Reference value is already inactive");
        active = false;
        validTo = Instant.now();
        updatedBy = required(actor, "actor", 96);
        updatedAt = validTo;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Value is too long");
        return value.trim();
    }

    public String category() { return id.category(); }
    public String code() { return id.code(); }
    public String label() { return label; }
    public boolean active() { return active; }
    public String attributesJson() { return attributesJson; }
    public Instant validFrom() { return validFrom; }
    public Instant validTo() { return validTo; }
    public String updatedBy() { return updatedBy; }
    public long version() { return version; }
}
