package com.staging.sg.common.ecommerce;

import com.staging.sg.common.issuing.PaymentIdentifierType;

import java.util.UUID;

public record EcommercePurchaseRequest(
        String schemaVersion,
        String transactionId,
        String correlationId,
        String idempotencyKey,
        String acquirerId,
        UUID profileId,
        String merchantOrderId,
        long amountMinor,
        String currency,
        PaymentIdentifierType paymentIdentifierType,
        String paymentIdentifier,
        String expiry,
        EcommerceNetworkRoute networkRoute,
        EcommerceAuthenticationStatus authenticationStatus,
        String eci,
        String cavv,
        String directoryServerTransactionId) {

    @Override
    public String toString() {
        return "EcommercePurchaseRequest[transactionId=" + transactionId
                + ", correlationId=" + correlationId
                + ", acquirerId=" + acquirerId
                + ", profileId=" + profileId
                + ", merchantOrderId=" + merchantOrderId
                + ", amountMinor=" + amountMinor
                + ", currency=" + currency
                + ", networkRoute=" + networkRoute
                + ", authenticationStatus=" + authenticationStatus
                + ", sensitiveData=REDACTED]";
    }
}
