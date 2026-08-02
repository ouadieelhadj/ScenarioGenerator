package com.staging.sg.visa.base2.common;

import java.util.List;

public record VisaBase2NetworkAck(String fileId, String status, int recordCount,
        String sha256, boolean replayed, String provenance, List<String> errors) {
    public VisaBase2NetworkAck {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
