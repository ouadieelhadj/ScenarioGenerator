package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsAReq(
        String messageType,
        String messageVersion,
        UUID threeDSServerTransId,
        String transactionId,
        String correlationId,
        ThreeDsProgram program,
        ThreeDsFlow flow,
        ThreeDsIssuerMode issuerMode,
        ThreeDsServerMode serverMode,
        String acquirerId,
        String merchantId,
        long amountMinor,
        String currency,
        String pan,
        String expiry) {

    @Override
    public String toString() {
        return "ThreeDsAReq[threeDSServerTransId=" + threeDSServerTransId
                + ", program=" + program + ", flow=" + flow
                + ", issuerMode=" + issuerMode + ", serverMode=" + serverMode
                + ", sensitiveData=REDACTED]";
    }
}
