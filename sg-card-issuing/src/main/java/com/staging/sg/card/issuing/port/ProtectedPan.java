package com.staging.sg.card.issuing.port;

public record ProtectedPan(
        String vaultReference,
        String maskedPan,
        String expiryYymm) {

    public ProtectedPan {
        if (vaultReference == null || vaultReference.isBlank()
                || maskedPan == null || !maskedPan.matches("\\d{6}\\*+\\d{4}")
                || expiryYymm == null || !expiryYymm.matches("\\d{4}")) {
            throw new IllegalArgumentException("Invalid protected PAN response");
        }
    }

    @Override
    public String toString() {
        return "ProtectedPan[vaultReference=REDACTED, maskedPan="
                + maskedPan + ", expiryYymm=" + expiryYymm + "]";
    }
}
