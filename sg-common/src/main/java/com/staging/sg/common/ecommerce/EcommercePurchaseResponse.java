package com.staging.sg.common.ecommerce;

public record EcommercePurchaseResponse(
        String schemaVersion,
        String transactionId,
        String status,
        String responseCode,
        String authorizationCode,
        EcommerceNetworkRoute networkRoute,
        long approvedAmountMinor,
        String currency,
        EcommerceAuthenticationStatus authenticationStatus,
        boolean retryable,
        boolean replayed) {
}
