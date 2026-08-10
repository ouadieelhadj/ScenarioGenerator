package com.staging.sg.onboarding.domain;

public enum TerminalRequestStatus {
    REQUESTED,
    PROVISIONING,
    PROVISIONED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCELLED
}
