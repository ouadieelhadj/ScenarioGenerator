package com.staging.sg.visa.base2.common;

public record VisaBase2PresentmentData(
        String transactionId, String pan, String arn, String acquirerBusinessId,
        String purchaseDateMmdd, long destinationAmountMinor, String destinationCurrency,
        long sourceAmountMinor, String sourceCurrency, String merchantName,
        String merchantCity, String merchantCountry, String mcc, String merchantZip,
        String merchantState, String aci, String authorizationCode, String posEntryMode,
        String transactionIdentifier, long authorizedAmountMinor,
        String authorizationCurrency, String authorizationResponseCode,
        String validationCode) {
}
