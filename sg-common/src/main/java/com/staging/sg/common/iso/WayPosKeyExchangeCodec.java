package com.staging.sg.common.iso;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** DE48 key-status/key-block codec from the OpenWay Extended Set. */
public final class WayPosKeyExchangeCodec {
    public static final int MAX_KEYS = 15;
    public static final int MAX_KEY_BLOCK_LENGTH = 127;

    public record KeyStatus(String keyId, String status, String keyType) {}
    public record KeyStatusDetails(
            String keyId, String status, String keyType, String kcv,
            String algorithm, String identificationScheme) {
        public KeyStatus statusOnly() {
            return new KeyStatus(keyId, status, keyType);
        }
    }
    public record KeyBlock(
            String keyId, String keyType, String kcv, String algorithm,
            String masterKeyId, String masterKeyType, byte[] ansiX917Block,
            String keyBlockFormat, String actionCode, String identificationScheme,
            String replacementKeyId) {
        public KeyBlock {
            ansiX917Block = ansiX917Block == null ? null : ansiX917Block.clone();
            keyBlockFormat = keyBlockFormat == null ? "1" : keyBlockFormat;
            actionCode = actionCode == null ? "0" : actionCode;
        }
        public KeyBlock(
                String keyId, String keyType, String kcv, String algorithm,
                String masterKeyId, String masterKeyType, byte[] ansiX917Block,
                String actionCode, String identificationScheme, String replacementKeyId) {
            this(keyId, keyType, kcv, algorithm, masterKeyId, masterKeyType,
                    ansiX917Block, "1", actionCode, identificationScheme,
                    replacementKeyId);
        }
        public KeyBlock(
                String keyId, String keyType, String kcv, String algorithm,
                String masterKeyId, String masterKeyType, byte[] ansiX917Block) {
            this(keyId, keyType, kcv, algorithm, masterKeyId, masterKeyType,
                    ansiX917Block, "1", "0", null, null);
        }
        @Override public byte[] ansiX917Block() {
            return ansiX917Block == null ? null : ansiX917Block.clone();
        }
    }

    private WayPosKeyExchangeCodec() {}

    public static byte[] encodeResponse(List<KeyBlock> keys) {
        if (keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("OpenWay accepts at most 15 key groups");
        }
        List<WayPosBerTlv.Tlv> outer = new ArrayList<>();
        int index = 1;
        for (KeyBlock key : keys) {
            validateResponseKey(key);
            List<WayPosBerTlv.Tlv> inner = new ArrayList<>();
            inner.add(text(0xDF24, key.keyType()));
            inner.add(text(0xDF20, key.keyId()));
            if (key.kcv() != null) inner.add(text(0xDF22, key.kcv()));
            inner.add(text(0xDF25, key.masterKeyId()));
            inner.add(text(0xDF28, key.masterKeyType()));
            if ("0".equals(key.actionCode()) || "1".equals(key.actionCode())) {
                inner.add(text(0xDF40, key.keyBlockFormat()));
                inner.add(new WayPosBerTlv.Tlv(0xDF41, key.ansiX917Block()));
            }
            inner.add(text(0xDF50, key.actionCode()));
            if (key.algorithm() != null) inner.add(text(0xDF23, key.algorithm()));
            if (key.identificationScheme() != null) {
                inner.add(text(0xDF26, key.identificationScheme()));
            }
            if (key.replacementKeyId() != null) {
                inner.add(text(0xDF29, key.replacementKeyId()));
            }
            outer.add(new WayPosBerTlv.Tlv(0xFF00 + index++, WayPosBerTlv.encode(inner)));
        }
        return WayPosBerTlv.encode(outer);
    }

    /**
     * Encodes the exact two-key envelope observed on the accepted Feitian F20
     * Way4 RKI response: TPK first, TAK second, a shared two-digit key ID and
     * two 112-byte DF40=2 protected key blocks. Optional tags that Way4 does
     * not put on the wire are deliberately omitted.
     */
    public static byte[] encodeWay4F20Response(List<KeyBlock> keys) {
        if (keys == null || keys.size() != 2) {
            throw new IllegalArgumentException(
                    "Way4 F20 RKI requires exactly one TPK and one TAK");
        }
        KeyBlock tpk = keys.stream()
                .filter(key -> "TPK".equals(key.keyType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing TPK"));
        KeyBlock tak = keys.stream()
                .filter(key -> "TAK".equals(key.keyType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing TAK"));
        validateWay4F20Key(tpk, "TPMK");
        validateWay4F20Key(tak, "TAMK");
        if (!tpk.keyId().equals(tak.keyId())) {
            throw new IllegalArgumentException(
                    "Way4 F20 TPK and TAK must share the same key ID");
        }
        return WayPosBerTlv.encode(List.of(
                way4F20Group(0xFF01, tpk),
                way4F20Group(0xFF02, tak)));
    }

    private static WayPosBerTlv.Tlv way4F20Group(int tag, KeyBlock key) {
        return new WayPosBerTlv.Tlv(tag, WayPosBerTlv.encode(List.of(
                text(0xDF24, key.keyType()),
                text(0xDF20, key.keyId()),
                text(0xDF25, key.masterKeyId()),
                text(0xDF28, key.masterKeyType()),
                text(0xDF40, key.keyBlockFormat()),
                new WayPosBerTlv.Tlv(0xDF41, key.ansiX917Block()))));
    }

    private static void validateWay4F20Key(
            KeyBlock key, String expectedMasterType) {
        if (key.keyId() == null || !key.keyId().matches("[0-9]{2}")) {
            throw new IllegalArgumentException(
                    "Way4 F20 key ID must contain exactly two ASCII digits");
        }
        if (!"00".equals(key.masterKeyId())
                || !expectedMasterType.equals(key.masterKeyType())) {
            throw new IllegalArgumentException(
                    key.keyType() + " must reference master key 00/"
                            + expectedMasterType);
        }
        byte[] block = key.ansiX917Block();
        if (!"2".equals(key.keyBlockFormat())
                || block == null || block.length != 112
                || block[0] != 'D' || block[1] != '0'
                || block[2] != '1' || block[3] != '1'
                || block[4] != '2') {
            throw new IllegalArgumentException(
                    key.keyType()
                            + " requires the observed DF40=2 D0112 block");
        }
    }

    public static List<KeyBlock> decodeResponse(byte[] data) {
        List<KeyBlock> result = new ArrayList<>();
        for (WayPosBerTlv.Tlv group : WayPosBerTlv.decode(data)) {
            if (group.tag() < 0xFF01 || group.tag() > 0xFF0F) continue;
            String id = null, type = null, kcv = null, algorithm = null;
            String masterId = null, masterType = null, format = null;
            String action = "0", scheme = null, replacement = null;
            byte[] block = null;
            for (WayPosBerTlv.Tlv item : WayPosBerTlv.decode(group.value())) {
                switch (item.tag()) {
                    case 0xDF20 -> id = text(item);
                    case 0xDF22 -> kcv = text(item);
                    case 0xDF23 -> algorithm = text(item);
                    case 0xDF24 -> type = text(item);
                    case 0xDF25 -> masterId = text(item);
                    case 0xDF26 -> scheme = text(item);
                    case 0xDF28 -> masterType = text(item);
                    case 0xDF29 -> replacement = text(item);
                    case 0xDF40 -> format = text(item);
                    case 0xDF41 -> block = item.value();
                    case 0xDF50 -> action = text(item);
                    default -> { /* Future OpenWay tags are deliberately ignored. */ }
                }
            }
            KeyBlock key = new KeyBlock(
                    id, type, kcv, algorithm, masterId, masterType, block,
                    format, action, scheme, replacement);
            validateResponseKey(key);
            result.add(key);
        }
        return List.copyOf(result);
    }

    /**
     * Splits complete BER-TLV groups without cutting a TLV between DE48 and
     * DE59. The returned list contains at most two fields.
     */
    public static List<byte[]> encodeResponseFields(List<KeyBlock> keys, int maxFieldLength) {
        if (maxFieldLength <= 0) throw new IllegalArgumentException("Invalid field length");
        List<WayPosBerTlv.Tlv> groups = WayPosBerTlv.decode(encodeResponse(keys));
        List<byte[]> fields = new ArrayList<>();
        List<WayPosBerTlv.Tlv> current = new ArrayList<>();
        for (WayPosBerTlv.Tlv group : groups) {
            List<WayPosBerTlv.Tlv> candidate = new ArrayList<>(current);
            candidate.add(group);
            if (WayPosBerTlv.encode(candidate).length > maxFieldLength) {
                if (current.isEmpty()) {
                    throw new IllegalArgumentException("A key group exceeds the ISO field capacity");
                }
                fields.add(WayPosBerTlv.encode(current));
                current.clear();
                current.add(group);
            } else {
                current.add(group);
            }
        }
        if (!current.isEmpty()) fields.add(WayPosBerTlv.encode(current));
        if (fields.size() > 2) {
            throw new IllegalArgumentException("Key response exceeds DE48 and DE59 capacity");
        }
        return List.copyOf(fields);
    }

    public static byte[] encodeStatuses(List<KeyStatus> statuses) {
        if (statuses.size() > MAX_KEYS) {
            throw new IllegalArgumentException("OpenWay accepts at most 15 key groups");
        }
        List<WayPosBerTlv.Tlv> outer = new ArrayList<>();
        int index = 1;
        for (KeyStatus status : statuses) {
            byte[] inner = WayPosBerTlv.encode(List.of(
                    text(0xDF20, status.keyId()),
                    text(0xDF21, status.status()),
                    text(0xDF24, status.keyType())));
            outer.add(new WayPosBerTlv.Tlv(0xFF00 + index++, inner));
        }
        return WayPosBerTlv.encode(outer);
    }

    public static byte[] encodeStatusDetails(List<KeyStatusDetails> statuses) {
        if (statuses.size() > MAX_KEYS) {
            throw new IllegalArgumentException("OpenWay accepts at most 15 key groups");
        }
        List<WayPosBerTlv.Tlv> outer = new ArrayList<>();
        int index = 1;
        for (KeyStatusDetails status : statuses) {
            List<WayPosBerTlv.Tlv> inner = new ArrayList<>();
            inner.add(text(0xDF20, status.keyId()));
            inner.add(text(0xDF21, status.status()));
            if (status.kcv() != null) inner.add(text(0xDF22, status.kcv()));
            if (status.algorithm() != null) {
                inner.add(text(0xDF23, status.algorithm()));
            }
            inner.add(text(0xDF24, status.keyType()));
            if (status.identificationScheme() != null) {
                inner.add(text(0xDF26, status.identificationScheme()));
            }
            outer.add(new WayPosBerTlv.Tlv(
                    0xFF00 + index++, WayPosBerTlv.encode(inner)));
        }
        return WayPosBerTlv.encode(outer);
    }

    public static List<KeyStatus> decodeStatuses(byte[] data) {
        return decodeStatusDetails(data).stream()
                .map(KeyStatusDetails::statusOnly)
                .toList();
    }

    public static List<KeyStatusDetails> decodeStatusDetails(byte[] data) {
        List<KeyStatusDetails> result = new ArrayList<>();
        for (WayPosBerTlv.Tlv group : WayPosBerTlv.decode(data)) {
            if ((group.tag() & 0xFF00) != 0xFF00) continue;
            String id = null, status = null, type = null;
            String kcv = null, algorithm = null, scheme = null;
            for (WayPosBerTlv.Tlv item : WayPosBerTlv.decode(group.value())) {
                if (item.tag() == 0xDF20) id = text(item);
                if (item.tag() == 0xDF21) status = text(item);
                if (item.tag() == 0xDF22) kcv = text(item);
                if (item.tag() == 0xDF23) algorithm = text(item);
                if (item.tag() == 0xDF24) type = text(item);
                if (item.tag() == 0xDF26) scheme = text(item);
            }
            if (id != null && status != null && type != null) {
                result.add(new KeyStatusDetails(
                        id, status, type, kcv, algorithm, scheme));
            }
        }
        return List.copyOf(result);
    }

    private static WayPosBerTlv.Tlv text(int tag, String value) {
        if (value == null) throw new IllegalArgumentException("Missing value for tag " + tag);
        return new WayPosBerTlv.Tlv(tag, value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String text(WayPosBerTlv.Tlv value) {
        return new String(value.value(), StandardCharsets.US_ASCII);
    }

    private static void validateResponseKey(KeyBlock key) {
        if (key.keyId() == null || key.keyId().isBlank()) {
            throw new IllegalArgumentException("DF20 Key ID is mandatory");
        }
        if (key.keyType() == null || key.keyType().isBlank()) {
            throw new IllegalArgumentException("DF24 Key Type is mandatory");
        }
        if (key.masterKeyId() == null || key.masterKeyId().isBlank()
                || key.masterKeyType() == null || key.masterKeyType().isBlank()) {
            throw new IllegalArgumentException("DF25/DF28 master key references are mandatory");
        }
        if (!List.of("0", "1", "2").contains(key.actionCode())) {
            throw new IllegalArgumentException("Unsupported DF50 action " + key.actionCode());
        }
        if (("0".equals(key.actionCode()) || "1".equals(key.actionCode()))
                && !List.of("1", "2").contains(key.keyBlockFormat())) {
            throw new IllegalArgumentException(
                    "Unsupported DF40 key-block format " + key.keyBlockFormat());
        }
        if (("0".equals(key.actionCode()) || "1".equals(key.actionCode()))
                && (key.ansiX917Block() == null
                || key.ansiX917Block().length == 0
                || key.ansiX917Block().length > MAX_KEY_BLOCK_LENGTH)) {
            throw new IllegalArgumentException("DF41 must contain 1..127 bytes");
        }
        if ("1".equals(key.actionCode())
                && (key.replacementKeyId() == null || key.replacementKeyId().isBlank())) {
            throw new IllegalArgumentException("DF29 is mandatory for key replacement");
        }
    }
}
