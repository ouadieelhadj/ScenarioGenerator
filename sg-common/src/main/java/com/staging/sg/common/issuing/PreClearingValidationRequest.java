package com.staging.sg.common.issuing;

import java.util.Map;

public record PreClearingValidationRequest(
        String schemaVersion,
        String issuerId,
        String callerId,
        String clearingRecordId,
        String correlationId,
        String idempotencyKey,
        PaymentIdentifierType paymentIdentifierType,
        String paymentIdentifier,
        String authorizationTransactionId,
        String authorizationCode,
        long amountMinor,
        String currency,
        String presentmentDateTime,
        Map<String, String> networkReferences,
        Map<String, String> attributes) {

    public PreClearingValidationRequest {
        networkReferences = networkReferences == null
                ? Map.of() : Map.copyOf(networkReferences);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String toString() {
        return "PreClearingValidationRequest[issuerId=" + issuerId
                + ", callerId=" + callerId
                + ", clearingRecordId=" + clearingRecordId
                + ", correlationId=" + correlationId
                + ", authorizationTransactionId=" + authorizationTransactionId
                + ", amountMinor=" + amountMinor
                + ", currency=" + currency
                + ", sensitiveData=REDACTED]";
    }
}
