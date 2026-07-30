package com.staging.sg.waypos.server.service;

import com.staging.sg.waypos.server.config.WayPosProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PanProtectionService {
    private final byte[] pepper;

    public PanProtectionService(WayPosProperties properties) {
        this.pepper = properties.panPepper().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String pan) {
        if (pan == null || !pan.matches("\\d{12,19}")) {
            throw new IllegalArgumentException("Invalid PAN");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            return HexFormat.of().formatHex(digest.digest(pan.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String mask(String pan) {
        int visibleStart = Math.min(6, pan.length() - 4);
        return pan.substring(0, visibleStart)
                + "*".repeat(pan.length() - visibleStart - 4)
                + pan.substring(pan.length() - 4);
    }
}
