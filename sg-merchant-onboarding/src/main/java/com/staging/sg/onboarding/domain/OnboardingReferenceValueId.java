package com.staging.sg.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class OnboardingReferenceValueId implements Serializable {
    @Column(length = 32)
    private String category;
    @Column(length = 64)
    private String code;

    protected OnboardingReferenceValueId() {}
    public OnboardingReferenceValueId(String category, String code) {
        this.category = category;
        this.code = code;
    }
    public String category() { return category; }
    public String code() { return code; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OnboardingReferenceValueId value)) return false;
        return Objects.equals(category, value.category) && Objects.equals(code, value.code);
    }
    @Override public int hashCode() { return Objects.hash(category, code); }
}
