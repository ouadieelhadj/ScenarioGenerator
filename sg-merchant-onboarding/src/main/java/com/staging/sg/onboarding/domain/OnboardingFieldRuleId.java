package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class OnboardingFieldRuleId implements Serializable {
    @Column(name = "merchant_type", length = 32) private String merchantType;
    @Column(name = "field_path", length = 160) private String fieldPath;
    protected OnboardingFieldRuleId() {}
    public OnboardingFieldRuleId(String merchantType, String fieldPath) {
        this.merchantType = merchantType; this.fieldPath = fieldPath;
    }
    public String merchantType() { return merchantType; }
    public String fieldPath() { return fieldPath; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnboardingFieldRuleId value)) return false;
        return Objects.equals(merchantType, value.merchantType) && Objects.equals(fieldPath, value.fieldPath);
    }
    @Override public int hashCode() { return Objects.hash(merchantType, fieldPath); }
}
