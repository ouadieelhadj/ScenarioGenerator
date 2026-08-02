package com.staging.sg.visa.common.online;

/** JSON transport used only between the member and the network simulator. */
public record VisaOnlineNetworkEnvelope(String schemaVersion, String transactionId,
        String correlationId, String idempotencyKey, String isoMessageBase64,
        String provenance) {
    public VisaOnlineNetworkEnvelope {
        if (!"1.0".equals(schemaVersion) || transactionId == null || correlationId == null
                || idempotencyKey == null || isoMessageBase64 == null
                || !"SIMULATED_NETWORK".equals(provenance)) {
            throw new IllegalArgumentException("Invalid Visa simulator envelope");
        }
    }
}
