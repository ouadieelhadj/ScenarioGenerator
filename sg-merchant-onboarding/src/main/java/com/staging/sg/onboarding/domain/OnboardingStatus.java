package com.staging.sg.onboarding.domain;

public enum OnboardingStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    QUEUED_FOR_PROVISIONING,
    PROVISIONING,
    PROVISIONED,
    PROVISIONING_FAILED
}
