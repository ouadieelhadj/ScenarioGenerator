package com.staging.sg.ecommerce.simulator.api;

public record MerchantCardPaymentRequest(
        String cardholder,
        String pan,
        String expiry) {
    @Override
    public String toString() {
        return "MerchantCardPaymentRequest[cardholder=REDACTED,sensitiveData=REDACTED]";
    }
}
