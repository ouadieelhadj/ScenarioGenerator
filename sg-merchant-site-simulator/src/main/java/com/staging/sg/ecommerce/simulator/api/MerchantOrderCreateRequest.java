package com.staging.sg.ecommerce.simulator.api;

import java.util.List;

public record MerchantOrderCreateRequest(List<MerchantOrderItemRequest> items) {
}
