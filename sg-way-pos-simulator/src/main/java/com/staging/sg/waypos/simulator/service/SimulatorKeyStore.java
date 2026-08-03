package com.staging.sg.waypos.simulator.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.waypos.simulator.config.SimulatorProperties;
import org.jpos.iso.ISOUtil;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Volatile key store for the TPE simulator. Clear keys exist only in this
 * simulator process and are never logged or returned by the REST API.
 */
@Component
public class SimulatorKeyStore {
    private final SimulatorProperties properties;
    private final Map<String, byte[]> workingKeys = new LinkedHashMap<>();
    private List<WayPosKeyExchangeCodec.KeyStatus> statuses = List.of();
    private byte[] activeTak;

    public SimulatorKeyStore(SimulatorProperties properties) {
        this.properties = properties;
        if (validKeyHex(properties.takHex())) {
            activeTak = ISOUtil.hex2byte(properties.takHex());
        }
    }

    public synchronized byte[] activeTak() {
        if (activeTak == null) {
            throw new IllegalStateException("A real initial TAK is required");
        }
        return activeTak.clone();
    }

    public synchronized List<WayPosKeyExchangeCodec.KeyStatus> statuses() {
        return statuses;
    }

    public synchronized List<WayPosKeyExchangeCodec.KeyStatusDetails>
            initialMasterKeyStatuses() throws Exception {
        MasterKey tamk = masterKey("TAMK");
        MasterKey tpmk = masterKey("TPMK");
        return List.of(
                masterStatus(tamk, "TAMK"),
                masterStatus(tpmk, "TPMK"));
    }

    public synchronized List<WayPosKeyExchangeCodec.KeyStatus> importBlocks(
            List<WayPosKeyExchangeCodec.KeyBlock> blocks) {
        List<WayPosKeyExchangeCodec.KeyStatus> results = new ArrayList<>();
        byte[] nextTak = null;
        for (WayPosKeyExchangeCodec.KeyBlock block : blocks) {
            String status;
            try {
                if ("2".equals(block.actionCode())) {
                    workingKeys.remove(keyName(block.keyType(), block.keyId()));
                    status = "0";
                } else {
                    byte[] clear = unwrap(block);
                    verifyKcv(clear, block.kcv());
                    workingKeys.put(keyName(block.keyType(), block.keyId()), clear.clone());
                    if ("TAK".equals(block.keyType())) nextTak = clear.clone();
                    Arrays.fill(clear, (byte) 0);
                    status = "0";
                }
            } catch (IllegalArgumentException e) {
                status = "2";
            } catch (Exception e) {
                status = "1";
            }
            results.add(new WayPosKeyExchangeCodec.KeyStatus(
                    block.keyId(), status, block.keyType()));
        }
        if (nextTak != null) {
            if (activeTak != null) Arrays.fill(activeTak, (byte) 0);
            activeTak = nextTak;
        }
        statuses = List.copyOf(results);
        return statuses;
    }

    private byte[] unwrap(WayPosKeyExchangeCodec.KeyBlock block) throws Exception {
        if (!"1".equals(block.keyBlockFormat())) {
            throw new IllegalArgumentException(
                    "DF40=2 requires the matching Thales HSM key-block implementation");
        }
        MasterKey masterKey = masterKey(block.masterKeyType());
        if (!masterKey.id().equals(block.masterKeyId())) {
            throw new IllegalArgumentException("Unknown master key reference");
        }
        if (!validKeyHex(masterKey.hex())) {
            throw new IllegalStateException("A real terminal master key is required");
        }
        byte[] wrapped = block.ansiX917Block();
        if ("HEX_ASCII".equalsIgnoreCase(properties.ansiX917BlockEncoding())) {
            wrapped = ISOUtil.hex2byte(new String(wrapped, StandardCharsets.US_ASCII));
        } else if (!"BINARY".equalsIgnoreCase(properties.ansiX917BlockEncoding())) {
            throw new IllegalArgumentException("Unsupported ANSI X9.17 block encoding");
        }
        byte[] master = ISOUtil.hex2byte(masterKey.hex());
        try {
            byte[] key24 = expandDesEde(master);
            String mode = defaultValue(properties.ansiX917CipherMode(), "ECB").toUpperCase();
            Cipher cipher;
            if ("CBC_ZERO_IV".equals(mode)) {
                cipher = Cipher.getInstance("DESede/CBC/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key24, "DESede"),
                        new IvParameterSpec(new byte[8]));
            } else if ("ECB".equals(mode)) {
                cipher = Cipher.getInstance("DESede/ECB/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key24, "DESede"));
            } else {
                throw new IllegalArgumentException("Unsupported ANSI X9.17 cipher mode");
            }
            byte[] clear = cipher.doFinal(wrapped);
            int expectedLength = switch (defaultValue(block.algorithm(), "T")) {
                case "D" -> 8;
                case "T" -> 16;
                case "C" -> 24;
                default -> throw new IllegalArgumentException("Unsupported key algorithm");
            };
            if (clear.length != expectedLength) {
                Arrays.fill(clear, (byte) 0);
                throw new IllegalArgumentException("Unexpected clear key length");
            }
            return clear;
        } finally {
            Arrays.fill(master, (byte) 0);
        }
    }

    private MasterKey masterKey(String type) {
        if ("TAMK".equalsIgnoreCase(type)
                && validKeyHex(properties.tamkHex())) {
            return new MasterKey(
                    defaultValue(properties.tamkId(), properties.masterKeyId()),
                    properties.tamkHex());
        }
        if ("TPMK".equalsIgnoreCase(type)
                && validKeyHex(properties.tpmkHex())) {
            return new MasterKey(
                    defaultValue(properties.tpmkId(), properties.masterKeyId()),
                    properties.tpmkHex());
        }
        if (type != null && type.equalsIgnoreCase(properties.masterKeyType())) {
            return new MasterKey(properties.masterKeyId(), properties.masterKeyHex());
        }
        throw new IllegalArgumentException("Unknown master key type");
    }

    private static WayPosKeyExchangeCodec.KeyStatusDetails masterStatus(
            MasterKey key, String type) throws Exception {
        if (!validKeyHex(key.hex()) || key.hex().length() != 48) {
            throw new IllegalStateException(
                    "A triple-length terminal master key is required");
        }
        byte[] clear = ISOUtil.hex2byte(key.hex());
        try {
            return new WayPosKeyExchangeCodec.KeyStatusDetails(
                    key.id(), "0", type, keyKcv(clear), "C", "0");
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    private static String keyKcv(byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(expandDesEde(key), "DESede"));
        return ISOUtil.hexString(cipher.doFinal(new byte[8])).substring(0, 6);
    }

    private static void verifyKcv(byte[] key, String expected) throws Exception {
        if (expected == null || expected.isBlank()) return;
        byte[] zeros = new byte[8];
        Cipher cipher;
        if (key.length == 8) {
            cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DES"));
        } else {
            cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(expandDesEde(key), "DESede"));
        }
        String actual = ISOUtil.hexString(cipher.doFinal(zeros)).substring(0, 6);
        if (!MessageDigest.isEqual(
                actual.toUpperCase().getBytes(StandardCharsets.US_ASCII),
                expected.toUpperCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("KCV mismatch");
        }
    }

    private static byte[] expandDesEde(byte[] key) {
        if (key.length == 24) return key.clone();
        if (key.length == 16) {
            byte[] result = new byte[24];
            System.arraycopy(key, 0, result, 0, 16);
            System.arraycopy(key, 0, result, 16, 8);
            return result;
        }
        if (key.length == 8) {
            byte[] result = new byte[24];
            System.arraycopy(key, 0, result, 0, 8);
            System.arraycopy(key, 0, result, 8, 8);
            System.arraycopy(key, 0, result, 16, 8);
            return result;
        }
        throw new IllegalArgumentException("DES master key must contain 8/16/24 bytes");
    }

    private static boolean validKeyHex(String value) {
        return value != null && value.matches("(?i)([0-9a-f]{16}|[0-9a-f]{32}|[0-9a-f]{48})");
    }

    private static String keyName(String type, String id) {
        return type + ":" + id;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record MasterKey(String id, String hex) {}
}
