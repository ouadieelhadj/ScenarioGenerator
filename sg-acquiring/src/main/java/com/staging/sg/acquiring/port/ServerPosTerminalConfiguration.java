package com.staging.sg.acquiring.port;

import java.util.UUID;

public record ServerPosTerminalConfiguration(
        UUID sourceTerminalDeviceId,
        UUID sourceDeviceContractId,
        String terminalId,
        String merchantId,
        boolean extendedSet,
        String macData,
        boolean macRequired,
        String initialBatchId) {
}
