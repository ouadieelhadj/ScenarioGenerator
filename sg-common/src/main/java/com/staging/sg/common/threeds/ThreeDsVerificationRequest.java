package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsVerificationRequest(
        String schemaVersion,
        String transactionId,
        UUID dsTransId,
        ThreeDsProgram program,
        String eci,
        String authenticationValue,
        String merchantReference,
        long amountMinor,
        String currency) {

    @Override
    public String toString() {
        return "ThreeDsVerificationRequest[transactionId=" + transactionId
                + ", dsTransId=" + dsTransId + ", program=" + program
                + ", eci=" + eci + ", amountMinor=" + amountMinor
                + ", currency=" + currency + ", authenticationValue=REDACTED]";
    }
}
