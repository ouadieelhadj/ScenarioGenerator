package com.staging.sg.acquiring.api;

import com.staging.sg.acquiring.domain.ProvisioningObjectStatus;

import java.util.List;
import java.util.UUID;

public record MerchantProvisioningResultV2(
        String schemaVersion,
        UUID merchantId,
        String merchantAcceptorId,
        String aggregateStatus,
        List<ObjectResult> objects) {
    public record ObjectResult(String objectType, UUID sourceObjectId,
            ProvisioningObjectStatus status, String externalReference,
            String allocatedIdentifier, String errorCode, String errorMessage) {}
}
