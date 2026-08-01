package com.staging.sg.acquiring.domain;

public enum AcceptanceChannel {
    TPE,
    ECOMMERCE,
    BOTH;

    public boolean supportsTpe() {
        return this == TPE || this == BOTH;
    }

    public boolean supportsEcommerce() {
        return this == ECOMMERCE || this == BOTH;
    }
}
