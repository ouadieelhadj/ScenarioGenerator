package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;

import java.util.UUID;

public record InteractiveCheckoutStartResponse(
        UUID checkoutId,
        String state,
        String challengeUrl,
        EcommercePurchaseResponse purchase) {

    public static InteractiveCheckoutStartResponse challenge(UUID checkoutId,
            String challengeUrl) {
        return new InteractiveCheckoutStartResponse(checkoutId,
                "CHALLENGE_REQUIRED", challengeUrl, null);
    }

    public static InteractiveCheckoutStartResponse completed(
            EcommercePurchaseResponse purchase) {
        return new InteractiveCheckoutStartResponse(null, "COMPLETED", null, purchase);
    }
}
