package com.staging.sg.acquiring.api;

import java.time.Instant;
import java.util.UUID;

public record MerchantProvisioningRequestedV2(UUID eventId, String eventType,
        String schemaVersion, UUID onboardingCaseId, String correlationId, Instant occurredAt,
        MerchantProvisioningRequestV2 payload) {
    public static final String EVENT_TYPE = "merchant.provisioning.requested";
    public static final String SCHEMA_VERSION = "2.0";
}
