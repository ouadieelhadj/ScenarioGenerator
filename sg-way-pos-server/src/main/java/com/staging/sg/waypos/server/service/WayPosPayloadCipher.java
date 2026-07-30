package com.staging.sg.waypos.server.service;

import com.staging.sg.waypos.server.config.WayPosProperties;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class WayPosPayloadCipher {
    private static final int IV_LENGTH = 12;
    private final byte[] key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public WayPosPayloadCipher(WayPosProperties properties) {
        String configured = properties.outboxKeyHex();
        if (configured == null || !configured.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalStateException(
                    "WAY_POS_OUTBOX_KEY_HEX must contain a real 256-bit AES key");
        }
        key = ISOUtil.hex2byte(configured);
        try {
            keyId = ISOUtil.hexString(Arrays.copyOf(
                    MessageDigest.getInstance("SHA-256").digest(key), 8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to identify outbox key", e);
        }
    }

    public Encrypted encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            return new Encrypted(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)),
                    iv, keyId);
        } catch (Exception e) {
            throw new IllegalStateException("Outbox encryption failed", e);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv, String encryptedKeyId) {
        if (!keyId.equals(encryptedKeyId)) {
            throw new IllegalStateException("Outbox encryption key is unavailable");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Outbox decryption failed", e);
        }
    }

    public record Encrypted(byte[] ciphertext, byte[] iv, String keyId) {}
}
