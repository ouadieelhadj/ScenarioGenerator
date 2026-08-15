package com.staging.sg.fraud.service;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ReferenceProtector {
    public String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Reference is required");
        if (value.replaceAll("[ -]", "").matches("[0-9]{12,19}")) {
            throw new IllegalArgumentException("Raw card numbers are forbidden; use a token reference");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
