package com.staging.sg.onboarding.port;

import java.util.List;
import java.util.UUID;

public record MerchantProvisioningResultV2(String schemaVersion, UUID merchantId,
        String merchantAcceptorId, String aggregateStatus, List<ObjectResult> objects) {
    public record ObjectResult(String objectType, UUID sourceObjectId, String status,
            String externalReference, String allocatedIdentifier,
            String errorCode, String errorMessage) {}

    public boolean hasRetryableObjects() {
        return objects != null && objects.stream().anyMatch(value -> "RETRYABLE".equals(value.status()));
    }

    public boolean isComplete() {
        return "COMPLETED".equals(aggregateStatus)
                || "SUCCEEDED".equals(aggregateStatus)
                || "PROVISIONED".equals(aggregateStatus);
    }
}
