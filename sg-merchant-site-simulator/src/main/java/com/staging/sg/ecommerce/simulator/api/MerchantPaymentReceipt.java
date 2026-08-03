package com.staging.sg.ecommerce.simulator.api;

import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;

public record MerchantPaymentReceipt(
        MerchantOrderResponse order,
        EcommercePurchaseResponse payment) {
}
