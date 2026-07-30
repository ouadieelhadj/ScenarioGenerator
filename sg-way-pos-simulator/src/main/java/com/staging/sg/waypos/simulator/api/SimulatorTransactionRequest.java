package com.staging.sg.waypos.simulator.api;

public record SimulatorTransactionRequest(
        String mti,
        String processingCode,
        String pan,
        String expiry,
        String amount,
        String entryMode,
        String conditionCode,
        String pinBlockHex,
        String emvDataHex,
        String rrn,
        String terminalId,
        String merchantId,
        Boolean macEnabled,
        String networkId,
        String securityAdditionalData,
        String fileDataHex,
        String keyDataHex,
        String overflowDataHex,
        String originalDataElements,
        String operationSpecificData,
        String privateData) {
}
