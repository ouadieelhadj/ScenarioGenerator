package com.staging.sg.softpos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class SoftPosHashing {
    private SoftPosHashing() {}
    public static String sha256(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Value is required");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
