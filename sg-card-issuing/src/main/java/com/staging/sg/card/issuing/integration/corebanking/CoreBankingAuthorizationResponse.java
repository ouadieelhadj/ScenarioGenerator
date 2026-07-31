package com.staging.sg.card.issuing.integration.corebanking;

public record CoreBankingAuthorizationResponse(
        String schemaVersion,
        String issuerId,
        String transactionId,
        String correlationId,
        Status status,
        String responseCode,
        long approvedAmountMinor,
        String fundingReference) {

    public enum Status {
        APPROVED,
        PARTIALLY_APPROVED,
        DECLINED,
        UNAVAILABLE
    }
}
