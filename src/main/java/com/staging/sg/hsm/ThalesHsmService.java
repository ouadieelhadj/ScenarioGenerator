package com.staging.sg.hsm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Thales payShield HSM Service.
 * Simulated mode by default (no real HSM needed).
 *
 * Key hierarchy :
 *   ZMK encrypted under KEK  (shared physically between Acquirer and Issuer)
 *   ZPK encrypted under ZMK
 *   ZAK encrypted under ZMK
 */
@Service
public class ThalesHsmService {

    private static final Logger log = LoggerFactory.getLogger(ThalesHsmService.class);

    @Value("${mc.hsm.simulated:true}")
    private boolean simulated;

    @Value("${mc.kek:0123456789ABCDEF0123456789ABCDEF}")
    private String kekHex;

    // Session keys
    private byte[] sessionZmk;
    private byte[] sessionZpk;
    private byte[] sessionZak;

    private final SecureRandom random = new SecureRandom();

    // ── Key generation ───────────────────────────────────────

    public HsmKeyResult generateZmk() {
        log.info("[HSM] Generating ZMK...");
        byte[] keyValue = new byte[16];
        random.nextBytes(keyValue);
        byte[] encryptedUnderKek = tripleDesEncrypt(keyValue, hexToBytes(kekHex));
        String kcv = computeKcv(keyValue);
        log.info("[HSM] ZMK generated — KCV={}", kcv);
        return HsmKeyResult.builder()
                .keyType(HsmKeyResult.KeyType.ZMK)
                .keyValue(keyValue)
                .keyEncryptedUnderKek(encryptedUnderKek)
                .keyCheckValue(kcv)
                .success(true)
                .build();
    }

    public HsmKeyResult generateZpk(byte[] zmk) {
        log.info("[HSM] Generating ZPK under ZMK...");
        byte[] keyValue = new byte[16];
        random.nextBytes(keyValue);
        byte[] encryptedUnderZmk = tripleDesEncrypt(keyValue, zmk);
        String kcv = computeKcv(keyValue);
        log.info("[HSM] ZPK generated — KCV={}", kcv);
        return HsmKeyResult.builder()
                .keyType(HsmKeyResult.KeyType.ZPK)
                .keyValue(keyValue)
                .keyEncryptedUnderZmk(encryptedUnderZmk)
                .keyCheckValue(kcv)
                .success(true)
                .build();
    }

    public HsmKeyResult generateZak(byte[] zmk) {
        log.info("[HSM] Generating ZAK under ZMK...");
        byte[] keyValue = new byte[16];
        random.nextBytes(keyValue);
        byte[] encryptedUnderZmk = tripleDesEncrypt(keyValue, zmk);
        String kcv = computeKcv(keyValue);
        log.info("[HSM] ZAK generated — KCV={}", kcv);
        return HsmKeyResult.builder()
                .keyType(HsmKeyResult.KeyType.ZAK)
                .keyValue(keyValue)
                .keyEncryptedUnderZmk(encryptedUnderZmk)
                .keyCheckValue(kcv)
                .success(true)
                .build();
    }

    // ── KEK operations ───────────────────────────────────────

    public byte[] encryptUnderKek(byte[] key) {
        return tripleDesEncrypt(key, hexToBytes(kekHex));
    }

    public byte[] decryptUnderKek(byte[] encryptedKey) {
        return tripleDesDecrypt(encryptedKey, hexToBytes(kekHex));
    }

    public byte[] decryptUnderZmk(byte[] encryptedKey) {
        if (sessionZmk == null) {
            log.warn("[HSM] ZMK not loaded yet!");
            return encryptedKey;
        }
        return tripleDesDecrypt(encryptedKey, sessionZmk);
    }

    // ── Session keys ─────────────────────────────────────────

    public void setSessionKeys(byte[] zmk, byte[] zpk, byte[] zak) {
        this.sessionZmk = zmk;
        this.sessionZpk = zpk;
        this.sessionZak = zak;
        log.info("[HSM] Session keys loaded — ZMK KCV={} ZPK KCV={} ZAK KCV={}",
                zmk != null ? computeKcv(zmk) : "null",
                zpk != null ? computeKcv(zpk) : "null",
                zak != null ? computeKcv(zak) : "null");
    }

    public byte[] getSessionZmk() { return sessionZmk; }
    public byte[] getSessionZpk() { return sessionZpk; }
    public byte[] getSessionZak() { return sessionZak; }

    public boolean isAvailable() { return simulated; }

    // ── Crypto ───────────────────────────────────────────────

    private byte[] tripleDesEncrypt(byte[] data, byte[] key) {
        try {
            byte[] key24 = ensure24Bytes(key);
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24, "DESede"));
            return cipher.doFinal(padTo8(data));
        } catch (Exception e) {
            log.error("[HSM] 3DES encrypt failed : {}", e.getMessage());
            return xor(data, key);
        }
    }

    private byte[] tripleDesDecrypt(byte[] data, byte[] key) {
        try {
            byte[] key24 = ensure24Bytes(key);
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key24, "DESede"));
            return cipher.doFinal(padTo8(data));
        } catch (Exception e) {
            log.error("[HSM] 3DES decrypt failed : {}", e.getMessage());
            return xor(data, key);
        }
    }

    public String computeKcv(byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] enc = cipher.doFinal(new byte[16]);
            return bytesToHex(enc).substring(0, 6).toUpperCase();
        } catch (Exception e) { return "UNKNOWN"; }
    }

    private byte[] ensure24Bytes(byte[] key) {
        if (key.length == 24) return key;
        byte[] key24 = new byte[24];
        System.arraycopy(key, 0, key24, 0, Math.min(key.length, 16));
        if (key.length == 16) System.arraycopy(key, 0, key24, 16, 8);
        return key24;
    }

    private byte[] padTo8(byte[] data) {
        if (data.length % 8 == 0) return data;
        int padded = ((data.length / 8) + 1) * 8;
        byte[] result = new byte[padded];
        System.arraycopy(data, 0, result, 0, data.length);
        return result;
    }

    private byte[] xor(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++)
            result[i] = (byte)(data[i] ^ key[i % key.length]);
        return result;
    }

    public String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    public byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i/2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        return data;
    }
}
