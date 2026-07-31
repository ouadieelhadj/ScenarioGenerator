package com.staging.sg.card.issuing.service;

import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.PreClearingValidationRequest;

final class IssuingContractValidator {
    private IssuingContractValidator() {
    }

    static void validate(IssuingAuthorizationRequest request) {
        if (request == null
                || !"1.0".equals(request.schemaVersion())
                || blank(request.issuerId())
                || blank(request.callerId())
                || blank(request.transactionId())
                || blank(request.correlationId())
                || blank(request.idempotencyKey())
                || request.operation() == null
                || request.paymentIdentifierType() == null
                || blank(request.paymentIdentifier())
                || request.amountMinor() < 0
                || request.currency() == null
                || !request.currency().matches("\\d{3}")) {
            throw new IllegalArgumentException("Invalid issuing authorization contract");
        }
        if (request.pinBlockHex() != null
                && (request.pinKeyDomain() == null
                || !request.pinBlockHex().matches("(?i)[0-9a-f]{16}"))) {
            throw new IllegalArgumentException("Invalid opaque PIN block metadata");
        }
    }

    static void validate(PreClearingValidationRequest request) {
        if (request == null
                || !"1.0".equals(request.schemaVersion())
                || blank(request.issuerId())
                || blank(request.callerId())
                || blank(request.clearingRecordId())
                || blank(request.correlationId())
                || blank(request.idempotencyKey())
                || request.paymentIdentifierType() == null
                || blank(request.paymentIdentifier())
                || request.amountMinor() < 0
                || request.currency() == null
                || !request.currency().matches("\\d{3}")) {
            throw new IllegalArgumentException("Invalid pre-clearing validation contract");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
