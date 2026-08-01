package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsCRes(
        String messageType,
        String messageVersion,
        UUID threeDSServerTransId,
        UUID dsTransId,
        UUID acsTransId,
        ThreeDsTransStatus transStatus,
        boolean challengeCompletionInd) {
}
