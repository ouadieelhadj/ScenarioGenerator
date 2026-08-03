package com.staging.sg.ecommerce.simulator.api;

public record MerchantOrderLine(
        String productId,
        String name,
        int quantity,
        long unitPriceMinor,
        long lineTotalMinor) {
}
