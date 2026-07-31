package com.staging.sg.card.issuing.port;

public class PaymentIdentifierNotFoundException
        extends RuntimeException {
    public PaymentIdentifierNotFoundException() {
        super("Unknown payment identifier");
    }
}
