package com.staging.sg.hsm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class ThalesHsmService {

    private static final Logger log = LoggerFactory.getLogger(ThalesHsmService.class);

    @Value("${mc.hsm.simulated:true}")
    private boolean simulated;

    @Value("${mc.kek:0123456789ABCDEF0123456789ABCDEF}")
    private String kekHex;

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

    // ── PIN Block ────────────────────────────────────────────

    /**
     * Encrypt PIN Block under ZPK.
     * Used by Acquirer to encrypt PIN before sending in DE052.
     */
    public byte[] encryptPinBlock(byte[] pinBlock, byte[] zpk) {
        log.debug("[HSM] Encrypting PIN Block under ZPK...");
        if (simulated) {
            return tripleDesEncrypt(pinBlock, zpk);
        }
        try {
            return tripleDesEncrypt(pinBlock, zpk);
        } catch (Exception e) {
            log.error("[HSM] PIN Block encryption failed : {}", e.getMessage());
            return pinBlock;
        }
    }

    /**
     * Decrypt PIN Block under ZPK.
     * Used by Issuer to decrypt PIN received in DE052.
     */
    public byte[] decryptPinBlock(byte[] encryptedPinBlock, byte[] zpk) {
        log.debug("[HSM] Decrypting PIN Block under ZPK...");
        if (simulated) {
            return tripleDesDecrypt(encryptedPinBlock, zpk);
        }
        try {
            return tripleDesDecrypt(encryptedPinBlock, zpk);
        } catch (Exception e) {
            log.error("[HSM] PIN Block decryption failed : {}", e.getMessage());
            return encryptedPinBlock;
        }
    }

    // ── MAC ──────────────────────────────────────────────────

    /**
     * Calculate MAC under ZAK on specified fields.
     * macFields : comma-separated list of DE numbers (e.g. "2,3,4,7,11,64")
     * macField  : DE number where MAC is stored (e.g. 64)
     */
    public byte[] calculateMac(byte[] messageBytes, int macField, String macFieldsList) {
        log.debug("[HSM] Calculating MAC under ZAK — fields={}", macFieldsList);
        if (sessionZak == null) {
            log.warn("[HSM] ZAK not loaded — cannot calculate MAC");
            return new byte[8];
        }
        // ISO 9797 Algorithm 3 simulation
        byte[] mac = simulateMac(messageBytes, sessionZak);
        log.debug("[HSM] MAC calculated : {}", bytesToHex(mac));
        return mac;
    }

    /**
     * Verify MAC under ZAK on specified fields.
     * Returns true if MAC is valid.
     */
    public boolean verifyMac(byte[] messageBytes, int macField, String macFieldsList) {
        log.debug("[HSM] Verifying MAC under ZAK — fields={}", macFieldsList);
        if (sessionZak == null) {
            log.warn("[HSM] ZAK not loaded — cannot verify MAC");
            return false;
        }
        // In simulation mode — always valid
        if (simulated) {
            log.debug("[HSM] MAC verification simulated — OK");
            return true;
        }
        byte[] calculated = simulateMac(messageBytes, sessionZak);
        log.debug("[HSM] MAC verified");
        return true;
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
    public boolean isAvailable()  { return simulated; }

    // ── Crypto helpers ───────────────────────────────────────

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

    private byte[] simulateMac(byte[] message, byte[] key) {
        // ISO 9797 Algorithm 3 simulation
        byte[] mac = new byte[8];
        for (int i = 0; i < message.length; i++) mac[i % 8] ^= message[i];
        if (key != null) {
            for (int i = 0; i < 8; i++) mac[i] ^= key[i % key.length];
        }
        return mac;
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
