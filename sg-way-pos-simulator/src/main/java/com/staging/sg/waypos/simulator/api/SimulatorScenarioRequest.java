package com.staging.sg.waypos.simulator.api;

public record SimulatorScenarioRequest(
        String pan,
        String expiry,
        String amount,
        String targetPan,
        String pinBlockHex,
        String emvDataHex,
        String terminalId,
        String merchantId,
        Boolean macEnabled,
        String batchId,
        String cardControlType) {
}
