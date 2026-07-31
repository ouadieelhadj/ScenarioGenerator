package com.staging.sg.card.issuing.port;

import com.staging.sg.common.issuing.IssuingOperation;

public interface FundingAuthorizationPort {
    FundingResult authorize(FundingCommand command);
    record FundingCommand(
            String issuerId, String fundingContractId,
            IssuingOperation operation, long amountMinor, String currency,
            String transactionId, String originalTransactionId,
            String correlationId, String idempotencyKey) {}
    record FundingResult(
            FundingStatus status, String responseCode,
            long approvedAmountMinor, String fundingReference) {}
    enum FundingStatus { APPROVED, PARTIALLY_APPROVED, DECLINED, UNAVAILABLE }
}
