package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;

import java.util.UUID;

public record MerchantPaymentStartResponse(
        UUID orderId,
        String orderReference,
        String state,
        UUID checkoutId,
        String challengeUrl,
        EcommercePurchaseResponse purchase) {
}
