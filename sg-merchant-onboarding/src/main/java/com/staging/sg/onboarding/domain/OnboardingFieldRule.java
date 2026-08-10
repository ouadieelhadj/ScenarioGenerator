package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "onboarding_field_rule")
public class OnboardingFieldRule {
    @EmbeddedId private OnboardingFieldRuleId id;
    @Column(nullable = false) private boolean required;
    @Column(name = "max_length") private Integer maxLength;
    @Column(nullable = false) private boolean active;
    protected OnboardingFieldRule() {}
    public static OnboardingFieldRule active(MerchantType type, String fieldPath,
            boolean required, Integer maxLength) {
        OnboardingFieldRule value = new OnboardingFieldRule();
        value.id = new OnboardingFieldRuleId(type.name(), fieldPath);
        value.required = required; value.maxLength = maxLength; value.active = true;
        return value;
    }
    public String fieldPath() { return id.fieldPath(); }
    public boolean required() { return required; }
    public Integer maxLength() { return maxLength; }
}
