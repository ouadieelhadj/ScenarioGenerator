package com.staging.sg.card.issuing.port;

public record ProtectedPan(
        String vaultReference,
        String clearPan,
        String maskedPan,
        String expiryYymm) {

    public ProtectedPan(
            String vaultReference, String maskedPan, String expiryYymm) {
        this(vaultReference, null, maskedPan, expiryYymm);
    }

    public ProtectedPan {
        if (vaultReference == null || vaultReference.isBlank()
                || maskedPan == null || !maskedPan.matches("\\d{6}\\*+\\d{4}")
                || (clearPan != null && !clearPan.matches("\\d{12,19}"))
                || expiryYymm == null || !expiryYymm.matches("\\d{4}")) {
            throw new IllegalArgumentException("Invalid protected PAN response");
        }
    }

    @Override
    public String toString() {
        return "ProtectedPan[token=REDACTED, pan=REDACTED, maskedPan="
                + maskedPan + ", expiryYymm=" + expiryYymm + "]";
    }
}
