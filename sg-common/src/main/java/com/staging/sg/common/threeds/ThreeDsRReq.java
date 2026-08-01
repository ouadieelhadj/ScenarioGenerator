package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsRReq(
        String messageType,
        String messageVersion,
        UUID threeDSServerTransId,
        UUID dsTransId,
        UUID acsTransId,
        ThreeDsProgram program,
        ThreeDsTransStatus transStatus,
        String eci,
        String authenticationValue,
        boolean sandboxEvidence) {
}
