package com.staging.sg.common.routing;

import java.util.Map;

public record RoutingTransactionResponse(
        String transactionId,
        String status,
        String posResponseCode,
        String networkResponseCode,
        String authorizationCode,
        String route,
        String approvedAmount,
        String arpcHex,
        boolean retryable,
        Map<String, String> attributes) {

    public static RoutingTransactionResponse decline(
            String id, String responseCode, String route) {
        return new RoutingTransactionResponse(
                id, "DECLINED", responseCode, responseCode,
                null, route, null, null, false, Map.of());
    }
}
