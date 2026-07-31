package com.staging.sg.common.issuing;

import java.util.Map;

public record IssuingAuthorizationResponse(
        String schemaVersion,
        String issuerId,
        String transactionId,
        String correlationId,
        IssuingDecisionStatus status,
        String internalResponseCode,
        String authorizationCode,
        long approvedAmountMinor,
        String currency,
        String arpcHex,
        boolean retryable,
        Map<String, String> attributes) {

    public IssuingAuthorizationResponse {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String toString() {
        return "IssuingAuthorizationResponse[issuerId=" + issuerId
                + ", transactionId=" + transactionId
                + ", correlationId=" + correlationId
                + ", status=" + status
                + ", internalResponseCode=" + internalResponseCode
                + ", approvedAmountMinor=" + approvedAmountMinor
                + ", currency=" + currency
                + ", sensitiveData=REDACTED]";
    }
}
