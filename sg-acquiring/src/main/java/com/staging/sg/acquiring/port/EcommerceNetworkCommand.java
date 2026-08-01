package com.staging.sg.acquiring.port;

import com.staging.sg.common.ecommerce.EcommerceAuthenticationStatus;
import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;

public record EcommerceNetworkCommand(
        String transactionId,
        String correlationId,
        String idempotencyKey,
        String pan,
        String expiry,
        long amountMinor,
        String currency,
        String stan,
        String rrn,
        String terminalId,
        String merchantId,
        EcommerceNetworkRoute route,
        EcommerceAuthenticationStatus authenticationStatus,
        String eci,
        String authenticationValue,
        String directoryServerTransactionId) {
}
