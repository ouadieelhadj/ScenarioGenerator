package com.staging.sg.waypos.server.network;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operational ISO trace that keeps the wire layout useful for packager
 * diagnostics without writing cardholder or cryptographic material to disk.
 */
final class WayPosSafeMessageTrace {
    private static final Logger log = LoggerFactory.getLogger("WAY-POS-ISO");

    private static final Set<Integer> BINARY_SENSITIVE_FIELDS = Set.of(
            47, 48, 52, 55, 59, 61, 64);
    private static final Set<Integer> TEXT_SENSITIVE_FIELDS = Set.of(
            2, 31, 34, 35, 36, 45, 62);

    private WayPosSafeMessageTrace() {}

    static void received(ISOMsg message) {
        write(message, Direction.INCOMING);
    }

    static void outgoing(ISOMsg message) {
        write(message, Direction.OUTGOING);
    }

    private static void write(ISOMsg message, Direction direction) {
        try {
            log.info("\n{}", render(message, direction));
        } catch (Exception e) {
            log.warn("[WAY-POS][ISO-{}] safe trace unavailable: {}",
                    direction, e.getClass().getSimpleName());
        }
    }

    static String renderReceived(ISOMsg message) throws Exception {
        return render(message, Direction.INCOMING);
    }

    static String renderOutgoing(ISOMsg message) throws Exception {
        return render(message, Direction.OUTGOING);
    }

    private static String render(ISOMsg message, Direction direction) throws Exception {
        String mti = message.getMTI();
        String processingCode = text(message, 3);
        StringBuilder trace = new StringBuilder(768);
        trace.append("============================================================\n")
                .append("WAYPOS MESSAGE ").append(direction.label).append(" - ")
                .append(messageName(mti, processingCode)).append('\n')
                .append("============================================================\n")
                .append("DIRECTION=").append(direction).append('\n')
                .append("MTI=").append(mti)
                .append(" DE3=").append(processingCode)
                .append(" STAN=").append(text(message, 11))
                .append(" RRN=").append(text(message, 37))
                .append(" TERMINAL=").append(text(message, 41))
                .append(" RC=").append(text(message, 39)).append('\n')
                .append("FIELDS=").append(presentFields(message)).append('\n')
                .append("RKI_METADATA=").append(rkiMetadata(message)).append('\n')
                .append("MASKED_FIELDS=")
                .append(maskedPresentFields(message)).append('\n');

        ISOMsg sanitized = sanitize(message);
        byte[] packed = sanitized.pack();
        trace.append("BITMAP=")
                .append(packed.length >= 10
                        ? ISOUtil.hexString(packed, 2, 8) : "UNAVAILABLE")
                .append('\n')
                .append("---------------- BUFFER DUMP SANITIZED ----------------\n")
                .append(bufferDump(packed))
                .append("---------------- FIELD DUMP SANITIZED -----------------\n")
                .append(fieldDump(message))
                .append("============================================================");
        return trace.toString();
    }

    private enum Direction {
        INCOMING("RECEIVED [INCOMING]"),
        OUTGOING("SENT [OUTGOING]");

        private final String label;

        Direction(String label) {
            this.label = label;
        }
    }

    private static ISOMsg sanitize(ISOMsg source) throws Exception {
        ISOMsg sanitized = (ISOMsg) source.clone();
        for (int field : TEXT_SENSITIVE_FIELDS) {
            if (!sanitized.hasField(field)) continue;
            String value = sanitized.getString(field);
            int length = value == null ? 0 : value.length();
            String replacement = field == 2 ? "0".repeat(length) : "X".repeat(length);
            sanitized.set(field, replacement);
        }
        for (int field : BINARY_SENSITIVE_FIELDS) {
            if (!sanitized.hasField(field)) continue;
            byte[] value = sanitized.getBytes(field);
            sanitized.set(field, new byte[value == null ? 0 : value.length]);
        }
        return sanitized;
    }

    private static String rkiMetadata(ISOMsg message) {
        List<WayPosKeyExchangeCodec.KeyStatusDetails> statuses = new ArrayList<>();
        try {
            if (message.hasField(48)) {
                statuses.addAll(WayPosKeyExchangeCodec.decodeStatusDetails(message.getBytes(48)));
            }
            if (message.hasField(59)) {
                statuses.addAll(WayPosKeyExchangeCodec.decodeStatusDetails(message.getBytes(59)));
            }
        } catch (Exception e) {
            return "UNDECODABLE(" + e.getClass().getSimpleName() + ")";
        }
        if (statuses.isEmpty()) return "NONE";
        return statuses.stream()
                .map(value -> "{type=" + safe(value.keyType())
                        + ",id=" + safe(value.keyId())
                        + ",status=" + safe(value.status())
                        + ",kcv=" + (value.kcv() == null ? "ABSENT" : "PRESENT")
                        + ",algorithm=" + safe(value.algorithm())
                        + ",scheme=" + safe(value.identificationScheme()) + "}")
                .collect(Collectors.joining(","));
    }

    private static String presentFields(ISOMsg message) {
        List<String> fields = new ArrayList<>();
        for (int field = 1; field <= message.getMaxField(); field++) {
            if (message.hasField(field)) fields.add(Integer.toString(field));
        }
        return String.join(",", fields);
    }

    private static String maskedPresentFields(ISOMsg message) {
        return java.util.stream.Stream.concat(
                        TEXT_SENSITIVE_FIELDS.stream(), BINARY_SENSITIVE_FIELDS.stream())
                .filter(message::hasField)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static String messageName(String mti, String processingCode) {
        if ("0800".equals(mti) && "960000".equals(processingCode)) {
            return "RKI INITIAL KEY CHANGE (0800/960000)";
        }
        if ("0800".equals(mti) && "930000".equals(processingCode)) {
            return "RKI CONFIRMATION / SIGN-ON (0800/930000)";
        }
        if ("0100".equals(mti)) return "PURCHASE AUTHORIZATION (0100)";
        if ("0200".equals(mti)) return "PURCHASE FINANCIAL REQUEST (0200)";
        return "ISO MESSAGE (" + mti + "/" + processingCode + ")";
    }

    private static String bufferDump(byte[] data) {
        final int width = 18;
        StringBuilder dump = new StringBuilder(data.length * 5);
        for (int offset = 0; offset < data.length; offset += width) {
            int count = Math.min(width, data.length - offset);
            StringBuilder ascii = new StringBuilder(width * 2);
            StringBuilder hex = new StringBuilder(width * 3);
            for (int index = 0; index < width; index++) {
                if (index < count) {
                    int unsigned = data[offset + index] & 0xFF;
                    ascii.append(unsigned >= 0x20 && unsigned <= 0x7E
                            ? (char) unsigned : '.').append(' ');
                    hex.append(String.format("%02X ", unsigned));
                } else {
                    ascii.append("  ");
                }
            }
            dump.append(String.format("%04X |%-36s| %s%n",
                    offset, ascii, hex.toString().stripTrailing()));
        }
        return dump.toString();
    }

    private static String fieldDump(ISOMsg message) throws Exception {
        StringBuilder dump = new StringBuilder(512);
        dump.append("- M.T.I      : ").append(safeMti(message)).append('\n');
        for (int field = 1; field <= message.getMaxField(); field++) {
            if (!message.hasField(field)) continue;
            Object component = message.getComponent(field) == null
                    ? null : message.getComponent(field).getValue();
            int length;
            String value;
            if (component instanceof byte[] bytes) {
                length = bytes.length;
                value = isSensitive(field) ? "<MASKED>" : ISOUtil.hexString(bytes);
            } else {
                String text = component == null ? "" : component.toString();
                length = text.length();
                value = isSensitive(field) ? "<MASKED>" : text;
            }
            dump.append(String.format("- FLD (%03d) : (%03d) : [%s]%n",
                    field, length, value));
        }
        return dump.toString();
    }

    private static boolean isSensitive(int field) {
        return TEXT_SENSITIVE_FIELDS.contains(field)
                || BINARY_SENSITIVE_FIELDS.contains(field);
    }

    private static String safeMti(ISOMsg message) {
        try {
            return message.getMTI();
        } catch (Exception e) {
            return "????";
        }
    }

    private static String text(ISOMsg message, int field) {
        return message.hasField(field) ? safe(message.getString(field)) : "-";
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replaceAll("[^A-Za-z0-9_.-]", "?");
    }
}
