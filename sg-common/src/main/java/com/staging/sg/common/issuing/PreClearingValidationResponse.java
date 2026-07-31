package com.staging.sg.common.issuing;

import java.util.List;
import java.util.Map;

public record PreClearingValidationResponse(
        String schemaVersion,
        String issuerId,
        String clearingRecordId,
        String correlationId,
        PreClearingVerdict verdict,
        String authorizationTransactionId,
        List<String> mismatches,
        boolean financialMutationPerformed,
        Map<String, String> attributes) {

    public PreClearingValidationResponse {
        mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (financialMutationPerformed) {
            throw new IllegalArgumentException(
                    "Pre-clearing validation must not mutate financial state");
        }
    }
}
