package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;

import java.util.UUID;

public record SimulatorPurchaseRequest(
        String transactionId,
        String correlationId,
        String idempotencyKey,
        String acquirerId,
        UUID profileId,
        String merchantOrderId,
        long amountMinor,
        String currency,
        String pan,
        String expiry,
        EcommerceNetworkRoute networkRoute) {

    @Override
    public String toString() {
        return "SimulatorPurchaseRequest[transactionId=" + transactionId
                + ", acquirerId=" + acquirerId
                + ", profileId=" + profileId
                + ", merchantOrderId=" + merchantOrderId
                + ", amountMinor=" + amountMinor
                + ", currency=" + currency
                + ", networkRoute=" + networkRoute
                + ", sensitiveData=REDACTED]";
    }
}
