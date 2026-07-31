package com.staging.sg.card.issuing.api;

public record RegisterCardRequest(String pan, String expiryYymm) {
    @Override
    public String toString() {
        return "RegisterCardRequest[pan=REDACTED, expiryYymm="
                + expiryYymm + "]";
    }
}
