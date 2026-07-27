package com.staging.sg.swam.lis.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ClearingIdentityService {
    private final byte[] fingerprintSalt;

    public ClearingIdentityService(String fingerprintSalt) {
        if (fingerprintSalt == null || fingerprintSalt.length() < 16) {
            throw new IllegalArgumentException("The PAN fingerprint salt must contain at least 16 characters");
        }
        this.fingerprintSalt = fingerprintSalt.getBytes(StandardCharsets.UTF_8);
    }

    public String panFingerprint(String pan) {
        if (pan == null || pan.isBlank()) {
            throw new IllegalArgumentException("PAN is required");
        }
        return sha256(fingerprintSalt, pan.getBytes(StandardCharsets.US_ASCII));
    }

    public String functionalKey(String bankMemberId, String rrn, String stan,
                                String authorizationCode, String transactionDate,
                                long amount, String currency) {
        // STAN is deliberately excluded: LIS 4.13 financial TCR0/TCR1 does not
        // carry it, so including DE11 would make cross-file reconciliation impossible.
        String canonical = String.join("|",
                safe(bankMemberId), safe(rrn), safe(authorizationCode),
                safe(transactionDate), Long.toString(amount), safe(currency));
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public static String canonicalTransactionDate(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    public String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return "********";
        }
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10)
                + pan.substring(pan.length() - 4);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
