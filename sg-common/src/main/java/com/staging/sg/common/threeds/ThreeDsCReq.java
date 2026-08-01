package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsCReq(
        String messageType,
        String messageVersion,
        UUID threeDSServerTransId,
        UUID dsTransId,
        UUID acsTransId,
        String challengeData) {

    @Override
    public String toString() {
        return "ThreeDsCReq[threeDSServerTransId=" + threeDSServerTransId
                + ", dsTransId=" + dsTransId + ", acsTransId=" + acsTransId
                + ", challengeData=REDACTED]";
    }
}
