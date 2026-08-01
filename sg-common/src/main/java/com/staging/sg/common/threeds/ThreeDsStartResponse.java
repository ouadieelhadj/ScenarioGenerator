package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsStartResponse(
        String messageVersion,
        UUID threeDSServerTransId,
        UUID dsTransId,
        UUID acsTransId,
        ThreeDsProgram program,
        ThreeDsTransStatus transStatus,
        String eci,
        String authenticationValue,
        String challengeUrl,
        boolean sandboxEvidence) {
}
