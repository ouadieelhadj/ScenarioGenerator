package com.staging.sg.ecommerce.simulator.api;

import java.util.List;

public record MerchantCatalogProduct(
        String id,
        String name,
        String category,
        String description,
        List<String> features,
        long unitPriceMinor,
        String currency,
        String visualCode,
        String badge) {
}
