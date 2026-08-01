package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import com.staging.sg.common.threeds.ThreeDsFlow;
import com.staging.sg.common.threeds.ThreeDsIssuerMode;
import com.staging.sg.common.threeds.ThreeDsProgram;

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
        EcommerceNetworkRoute networkRoute,
        MerchantSiteType siteType,
        ThreeDsProgram threeDsProgram,
        ThreeDsFlow threeDsFlow,
        ThreeDsIssuerMode issuerMode,
        String challengeData) {

    public SimulatorPurchaseRequest(String transactionId, String correlationId,
            String idempotencyKey, String acquirerId, UUID profileId,
            String merchantOrderId, long amountMinor, String currency,
            String pan, String expiry, EcommerceNetworkRoute networkRoute) {
        this(transactionId, correlationId, idempotencyKey, acquirerId, profileId,
                merchantOrderId, amountMinor, currency, pan, expiry, networkRoute,
                MerchantSiteType.NATIONAL, null, ThreeDsFlow.NOT_REQUESTED,
                null, null);
    }

    @Override
    public String toString() {
        return "SimulatorPurchaseRequest[transactionId=" + transactionId
                + ", acquirerId=" + acquirerId
                + ", profileId=" + profileId
                + ", merchantOrderId=" + merchantOrderId
                + ", amountMinor=" + amountMinor
                + ", currency=" + currency
                + ", networkRoute=" + networkRoute
                + ", siteType=" + siteType
                + ", threeDsProgram=" + threeDsProgram
                + ", threeDsFlow=" + threeDsFlow
                + ", issuerMode=" + issuerMode
                + ", sensitiveData=REDACTED]";
    }
}
