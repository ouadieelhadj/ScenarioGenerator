package com.staging.sg.common.threeds;

import java.util.UUID;

public record ThreeDsRRes(
        String messageType,
        String messageVersion,
        UUID threeDSServerTransId,
        UUID dsTransId,
        UUID acsTransId,
        boolean accepted) {
}
