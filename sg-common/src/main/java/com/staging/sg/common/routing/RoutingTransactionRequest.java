package com.staging.sg.common.routing;

import java.util.Map;

/** Versioned network-neutral transaction contract used only between services. */
public record RoutingTransactionRequest(
        String schemaVersion,
        String transactionId,
        String correlationId,
        String idempotencyKey,
        String operation,
        String sourceMti,
        String processingCode,
        String pan,
        String expiry,
        String amount,
        String currency,
        String stan,
        String rrn,
        String terminalId,
        String merchantId,
        String pinBlockHex,
        String emvDataHex,
        String originalTransactionId,
        Map<String, String> attributes) {
}
