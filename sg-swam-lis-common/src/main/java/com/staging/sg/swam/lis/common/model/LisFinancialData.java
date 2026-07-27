package com.staging.sg.swam.lis.common.model;

import java.time.LocalDateTime;

/** Normalized source data required to encode one LIS financial presentation. */
public record LisFinancialData(
        String transactionType, String pan, String expiryDate, String stan, String rrn,
        String authorizationCode, LocalDateTime transactionAt, String merchantId,
        String merchantName, String merchantCity, String merchantCountry, String mcc,
        String terminalId, String acquirerInstitutionId, String issuerInstitutionId,
        long transactionAmount, String transactionCurrency, long settlementAmount,
        String settlementCurrency, Long billingAmount, String billingCurrency,
        String cardSequenceNumber, String ecommerceIndicator, int clearingCycle) {
}
