package com.staging.sg.common.issuing;

import java.util.Map;

public record IssuingAuthorizationRequest(
        String schemaVersion,
        String issuerId,
        String callerId,
        String transactionId,
        String correlationId,
        String idempotencyKey,
        IssuingOperation operation,
        String originalTransactionId,
        PaymentIdentifierType paymentIdentifierType,
        String paymentIdentifier,
        long amountMinor,
        String currency,
        String localTransactionDateTime,
        String terminalId,
        String merchantId,
        String merchantCategoryCode,
        String countryCode,
        boolean cardPresent,
        boolean ecommerce,
        String pinBlockHex,
        String pinKeyDomain,
        String emvDataHex,
        Map<String, String> attributes) {

    public IssuingAuthorizationRequest {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public String toString() {
        return "IssuingAuthorizationRequest[issuerId=" + issuerId
                + ", callerId=" + callerId
                + ", transactionId=" + transactionId
                + ", correlationId=" + correlationId
                + ", operation=" + operation
                + ", paymentIdentifierType=" + paymentIdentifierType
                + ", amountMinor=" + amountMinor
                + ", currency=" + currency
                + ", sensitiveData=REDACTED]";
    }
}
