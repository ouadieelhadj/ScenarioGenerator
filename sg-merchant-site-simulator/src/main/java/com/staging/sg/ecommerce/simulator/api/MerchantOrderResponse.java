package com.staging.sg.ecommerce.simulator.api;

import java.util.List;
import java.util.UUID;

public record MerchantOrderResponse(
        UUID orderId,
        String orderReference,
        List<MerchantOrderLine> lines,
        long totalMinor,
        String currency,
        String status) {
}
