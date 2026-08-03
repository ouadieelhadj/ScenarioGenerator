package com.staging.sg.common.ecommerce;

public record EcommerceRoutePreviewRequest(String paymentIdentifier) {
    @Override
    public String toString() {
        return "EcommerceRoutePreviewRequest[sensitiveData=REDACTED]";
    }
}
