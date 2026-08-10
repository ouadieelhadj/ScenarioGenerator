package com.staging.sg.acquiring.domain;

public enum ProvisioningObjectStatus {
    PENDING,
    IN_PROGRESS,
    PROVISIONED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}
