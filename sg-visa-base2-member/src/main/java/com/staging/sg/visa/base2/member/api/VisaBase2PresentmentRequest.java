package com.staging.sg.visa.base2.member.api;

public record VisaBase2PresentmentRequest(String schemaVersion, String transactionId,
        String correlationId, String pan, String purchaseDateMmdd, long amountMinor,
        String currency, String merchantName, String merchantCity, String merchantCountry,
        String mcc, String merchantZip, String merchantState, String posEntryMode,
        String aci, String authorizationCode, String visaTransactionId,
        String authorizationResponseCode, String validationCode) {
    @Override public String toString() {
        return "VisaBase2PresentmentRequest[transactionId=" + transactionId
                + ", correlationId=" + correlationId + ", amountMinor=" + amountMinor
                + ", currency=" + currency + ", sensitiveData=REDACTED]";
    }
}
