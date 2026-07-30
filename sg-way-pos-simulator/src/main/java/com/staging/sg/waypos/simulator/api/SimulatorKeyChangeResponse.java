package com.staging.sg.waypos.simulator.api;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;

import java.util.List;

public record SimulatorKeyChangeResponse(
        String responseCode,
        boolean responseMacVerified,
        List<WayPosKeyExchangeCodec.KeyStatus> importedKeyStatuses,
        boolean confirmationSent,
        String confirmationResponseCode,
        boolean confirmationMacVerified) {
}
