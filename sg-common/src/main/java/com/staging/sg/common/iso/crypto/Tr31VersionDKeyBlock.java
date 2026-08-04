package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/** Minimal ANSI X9.143/TR-31 version-D wrapper used by the Way4 F20 RKI. */
public final class Tr31VersionDKeyBlock {
    public static final int BLOCK_ASCII_LENGTH = 112;
    private static final int HEADER_LENGTH = 16;
    private static final int MAC_LENGTH = 16;
    private static final int AES_BLOCK_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tr31VersionDKeyBlock() {}

    public static String wrap(
            byte[] kbpk, byte[] clearKey, String usage,
            String modeOfUse, String keyVersion, String exportability)
            throws Exception {
        byte[] padding = new byte[14];
        RANDOM.nextBytes(padding);
        return wrap(kbpk, clearKey, usage, modeOfUse, keyVersion,
                exportability, padding);
    }

    static String wrap(
            byte[] kbpk, byte[] clearKey, String usage,
            String modeOfUse, String keyVersion, String exportability,
            byte[] padding) throws Exception {
        validate(kbpk, clearKey, usage, modeOfUse, keyVersion,
                exportability, padding);
        String header = "D0112" + usage + "T" + modeOfUse
                + keyVersion + exportability + "0000";
        byte[][] bindingKeys = deriveBindingKeys(kbpk);
        byte[] payload = new byte[32];
        int bitLength = clearKey.length * 8;
        payload[0] = (byte) (bitLength >>> 8);
        payload[1] = (byte) bitLength;
        System.arraycopy(clearKey, 0, payload, 2, clearKey.length);
        System.arraycopy(padding, 0, payload, 2 + clearKey.length,
                padding.length);
        byte[] authenticated = concatenate(
                header.getBytes(StandardCharsets.US_ASCII), payload);
        byte[] mac = aesCmac(bindingKeys[1], authenticated);
        byte[] encrypted = aesCbc(bindingKeys[0], mac, payload,
                Cipher.ENCRYPT_MODE);
        String result = header + ISOUtil.hexString(encrypted)
                + ISOUtil.hexString(mac);
        Arrays.fill(payload, (byte) 0);
        Arrays.fill(bindingKeys[0], (byte) 0);
        Arrays.fill(bindingKeys[1], (byte) 0);
        if (result.length() != BLOCK_ASCII_LENGTH
                || !result.startsWith("D0112")) {
            throw new IllegalStateException(
                    "TR-31 D block must contain exactly 112 ASCII characters");
        }
        return result;
    }

    public static byte[] unwrap(byte[] kbpk, String block) throws Exception {
        if (block == null || block.length() != BLOCK_ASCII_LENGTH
                || !block.startsWith("D0112")) {
            throw new IllegalArgumentException("Invalid TR-31 D0112 block");
        }
        byte[][] bindingKeys = deriveBindingKeys(kbpk);
        byte[] encrypted = ISOUtil.hex2byte(
                block.substring(HEADER_LENGTH, BLOCK_ASCII_LENGTH - 32));
        byte[] suppliedMac = ISOUtil.hex2byte(
                block.substring(BLOCK_ASCII_LENGTH - 32));
        byte[] payload = aesCbc(bindingKeys[0], suppliedMac, encrypted,
                Cipher.DECRYPT_MODE);
        byte[] authenticated = concatenate(
                block.substring(0, HEADER_LENGTH)
                        .getBytes(StandardCharsets.US_ASCII), payload);
        byte[] expectedMac = aesCmac(bindingKeys[1], authenticated);
        if (!MessageDigest.isEqual(expectedMac, suppliedMac)) {
            throw new SecurityException("TR-31 key-block MAC mismatch");
        }
        int bitLength = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        if ((bitLength & 7) != 0 || bitLength <= 0
                || bitLength / 8 > payload.length - 2) {
            throw new IllegalArgumentException("Invalid wrapped-key length");
        }
        byte[] clear = Arrays.copyOfRange(payload, 2, 2 + bitLength / 8);
        Arrays.fill(payload, (byte) 0);
        Arrays.fill(bindingKeys[0], (byte) 0);
        Arrays.fill(bindingKeys[1], (byte) 0);
        return clear;
    }

    private static byte[][] deriveBindingKeys(byte[] kbpk) throws Exception {
        if (kbpk == null || !(kbpk.length == 16
                || kbpk.length == 24 || kbpk.length == 32)) {
            throw new IllegalArgumentException(
                    "TR-31 D KBPK must be AES-128, AES-192 or AES-256");
        }
        byte[] cmacK2 = cmacSubkeys(kbpk)[1];
        return new byte[][]{
                deriveKey(kbpk, cmacK2, 0),
                deriveKey(kbpk, cmacK2, 1)
        };
    }

    private static byte[] deriveKey(
            byte[] kbpk, byte[] cmacK2, int usage) throws Exception {
        int bits = kbpk.length * 8;
        int algorithm = switch (bits) {
            case 128 -> 2;
            case 192 -> 3;
            case 256 -> 4;
            default -> throw new IllegalArgumentException("Unsupported KBPK");
        };
        int parts = (kbpk.length + AES_BLOCK_LENGTH - 1) / AES_BLOCK_LENGTH;
        byte[] derived = new byte[parts * AES_BLOCK_LENGTH];
        for (int part = 1; part <= parts; part++) {
            byte[] constant = new byte[AES_BLOCK_LENGTH];
            constant[0] = (byte) part;
            constant[1] = 0;
            constant[2] = (byte) usage;
            constant[3] = 0;
            constant[4] = 0;
            constant[5] = (byte) algorithm;
            constant[6] = (byte) (bits >>> 8);
            constant[7] = (byte) bits;
            constant[8] = (byte) 0x80;
            byte[] piece = aesBlock(kbpk, xor(cmacK2, constant));
            System.arraycopy(piece, 0, derived,
                    (part - 1) * AES_BLOCK_LENGTH, AES_BLOCK_LENGTH);
        }
        return Arrays.copyOf(derived, kbpk.length);
    }

    private static byte[] aesCmac(byte[] key, byte[] message)
            throws Exception {
        byte[][] subkeys = cmacSubkeys(key);
        int blocks = Math.max(1,
                (message.length + AES_BLOCK_LENGTH - 1) / AES_BLOCK_LENGTH);
        boolean complete = message.length > 0
                && message.length % AES_BLOCK_LENGTH == 0;
        byte[] state = new byte[AES_BLOCK_LENGTH];
        for (int i = 0; i < blocks - 1; i++) {
            byte[] current = Arrays.copyOfRange(message,
                    i * AES_BLOCK_LENGTH, (i + 1) * AES_BLOCK_LENGTH);
            state = aesBlock(key, xor(state, current));
        }
        byte[] last = new byte[AES_BLOCK_LENGTH];
        int offset = (blocks - 1) * AES_BLOCK_LENGTH;
        int remaining = Math.max(0, message.length - offset);
        if (remaining > 0) {
            System.arraycopy(message, offset, last, 0, remaining);
        }
        if (complete) {
            last = xor(last, subkeys[0]);
        } else {
            last[remaining] = (byte) 0x80;
            last = xor(last, subkeys[1]);
        }
        return aesBlock(key, xor(state, last));
    }

    private static byte[][] cmacSubkeys(byte[] key) throws Exception {
        byte[] l = aesBlock(key, new byte[AES_BLOCK_LENGTH]);
        byte[] k1 = shiftLeft(l);
        if ((l[0] & 0x80) != 0) k1[AES_BLOCK_LENGTH - 1] ^= (byte) 0x87;
        byte[] k2 = shiftLeft(k1);
        if ((k1[0] & 0x80) != 0) k2[AES_BLOCK_LENGTH - 1] ^= (byte) 0x87;
        return new byte[][]{k1, k2};
    }

    private static byte[] shiftLeft(byte[] value) {
        byte[] result = new byte[value.length];
        int carry = 0;
        for (int i = value.length - 1; i >= 0; i--) {
            int current = value[i] & 0xFF;
            result[i] = (byte) ((current << 1) | carry);
            carry = current >>> 7;
        }
        return result;
    }

    private static byte[] aesBlock(byte[] key, byte[] input)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(input);
    }

    private static byte[] aesCbc(
            byte[] key, byte[] iv, byte[] input, int mode) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(iv));
        return cipher.doFinal(input);
    }

    private static byte[] xor(byte[] left, byte[] right) {
        byte[] result = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = (byte) (left[i] ^ right[i]);
        }
        return result;
    }

    private static byte[] concatenate(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static void validate(
            byte[] kbpk, byte[] clearKey, String usage, String modeOfUse,
            String keyVersion, String exportability, byte[] padding) {
        if (kbpk == null || !(kbpk.length == 16
                || kbpk.length == 24 || kbpk.length == 32)) {
            throw new IllegalArgumentException("Invalid AES KBPK length");
        }
        if (clearKey == null || clearKey.length != 16) {
            throw new IllegalArgumentException(
                    "Way4 F20 working key must contain 16 bytes");
        }
        if (usage == null || !usage.matches("[A-Z0-9]{2}")
                || modeOfUse == null || !modeOfUse.matches("[A-Z]")) {
            throw new IllegalArgumentException("Invalid TR-31 usage or mode");
        }
        if (keyVersion == null || !keyVersion.matches("[0-9]{2}")) {
            throw new IllegalArgumentException(
                    "TR-31 key version must contain two digits");
        }
        if (exportability == null || !exportability.matches("[A-Z]")) {
            throw new IllegalArgumentException("Invalid TR-31 exportability");
        }
        if (padding == null || padding.length != 14) {
            throw new IllegalArgumentException(
                    "D0112 wrapping requires 14 padding bytes");
        }
    }
}
