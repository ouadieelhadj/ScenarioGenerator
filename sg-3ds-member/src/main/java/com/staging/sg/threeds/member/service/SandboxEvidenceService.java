package com.staging.sg.threeds.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Service
public class SandboxEvidenceService {
    private final byte[] key;

    public SandboxEvidenceService(
            @Value("${three-ds.sandbox.hmac-key:}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public String evidence(String context) {
        requireKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(Arrays.copyOf(
                    mac.doFinal(context.getBytes(StandardCharsets.UTF_8)), 20));
        } catch (Exception e) {
            throw new IllegalStateException("Sandbox evidence generation failed", e);
        }
    }

    public String fingerprint(String value) {
        if (value == null) return null;
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Evidence fingerprint failed", e);
        }
    }

    public String panFingerprint(String pan) { return fingerprint("PAN|" + pan); }

    public boolean otpMatches(String supplied, String expected) {
        if (supplied == null || expected == null) return false;
        return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private void requireKey() {
        if (key.length < 32) {
            throw new IllegalStateException(
                    "THREE_DS_SANDBOX_HMAC_KEY must contain at least 32 characters");
        }
    }
}
