package com.staging.sg.waypos.simulator.api;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;

import java.util.List;

public record SimulatorKeyConfirmationResponse(
        String responseCode,
        boolean responseMacVerified,
        List<WayPosKeyExchangeCodec.KeyStatus> keyStatuses,
        boolean confirmed) {
}
