package com.staging.sg.visa.base2.common;

public record VisaBase2NetworkFileEnvelope(String schemaVersion, String fileId,
        String correlationId, String ctfBase64, String sha256, String provenance) {
    public VisaBase2NetworkFileEnvelope {
        if (!"1.0".equals(schemaVersion) || fileId == null || correlationId == null
                || ctfBase64 == null || sha256 == null
                || !"SIMULATED_NETWORK".equals(provenance)) {
            throw new IllegalArgumentException("Invalid Base II simulator envelope");
        }
    }
}
