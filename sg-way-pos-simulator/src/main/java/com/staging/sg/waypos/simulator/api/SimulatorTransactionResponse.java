package com.staging.sg.waypos.simulator.api;

public record SimulatorTransactionResponse(
        String requestMti,
        String responseMti,
        String stan,
        String rrn,
        String responseCode,
        String authorizationCode,
        boolean approved,
        boolean macVerified,
        long elapsedMillis,
        String batchId,
        String emvResponseHex) {
}
