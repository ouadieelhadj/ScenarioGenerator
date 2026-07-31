package com.staging.sg.card.issuing.integration.corebanking;

import com.staging.sg.common.issuing.IssuingOperation;

public record CoreBankingAuthorizationRequest(
        String schemaVersion,
        String issuerId,
        String fundingContractId,
        IssuingOperation operation,
        long amountMinor,
        String currency,
        String transactionId,
        String originalTransactionId,
        String correlationId,
        String idempotencyKey) {
}
