package com.staging.sg.onboarding.domain;

public enum EcommerceStoreRequestStatus {
    REQUESTED,
    PROVISIONING,
    PROVISIONED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCELLED
}
