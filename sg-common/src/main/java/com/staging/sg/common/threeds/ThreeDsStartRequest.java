package com.staging.sg.common.threeds;

public record ThreeDsStartRequest(
        String schemaVersion,
        String transactionId,
        String correlationId,
        ThreeDsProgram program,
        ThreeDsFlow flow,
        ThreeDsIssuerMode issuerMode,
        String acquirerId,
        String merchantId,
        long amountMinor,
        String currency,
        String pan,
        String expiry) {

    @Override
    public String toString() {
        return "ThreeDsStartRequest[transactionId=" + transactionId
                + ", program=" + program + ", flow=" + flow
                + ", issuerMode=" + issuerMode + ", amountMinor="
                + amountMinor + ", currency=" + currency
                + ", sensitiveData=REDACTED]";
    }
}
