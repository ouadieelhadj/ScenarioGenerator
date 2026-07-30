package com.staging.sg.common.iso;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.util.ArrayList;
import java.util.List;

/** Structural validation shared by WayPosServer and wayPosSimulator. */
public final class WayPosMessageValidator {
    private WayPosMessageValidator() {}

    public static void validateRequest(ISOMsg message) throws ISOException {
        String mti = message.getMTI();
        List<Integer> required = new ArrayList<>(List.of(7, 11, 41, 63));
        if (mti.startsWith("01") || mti.startsWith("02")) {
            required.addAll(List.of(2, 3, 4, 14, 22, 25, 37, 49));
        } else if (mti.startsWith("04")) {
            required.addAll(List.of(2, 3, 4, 24, 37, 49, 60));
        } else if (mti.startsWith("05")) {
            required.addAll(List.of(3, 60));
        } else if ("0320".equals(mti) || "0321".equals(mti)) {
            required.addAll(List.of(3, 4, 49));
        } else if ("0302".equals(mti)) {
            if (!message.hasField(47)) {
                throw new ISOException("Missing mandatory fields [47]");
            }
        } else if (mti.startsWith("08")) {
            required.add(3);
        } else if (!mti.startsWith("08") && !mti.startsWith("03")
                && !mti.startsWith("91") && !mti.startsWith("97")) {
            throw new ISOException("Unsupported Way POS MTI " + mti);
        }
        List<Integer> missing = required.stream()
                .filter(field -> !message.hasField(field)).toList();
        if (!missing.isEmpty()) {
            throw new ISOException("Missing mandatory fields " + missing);
        }
        if ("000000".equals(message.getString(11))) {
            throw new ISOException("DE11 must be non-zero");
        }
        if (!containsDe63Tag(message.getString(63), "SV")) {
            throw new ISOException("DE63 tag SV is mandatory");
        }
        validateOperationSpecificFields(message);
        if (message.hasField(55) && message.getBytes(55).length == 0) {
            throw new ISOException("Empty DE55");
        }
    }

    private static void validateOperationSpecificFields(ISOMsg message)
            throws ISOException {
        String mti = message.getMTI();
        String processingCode = message.hasField(3) ? message.getString(3) : "";
        String prefix = processingCode.length() >= 2
                ? processingCode.substring(0, 2) : processingCode;
        if (mti.startsWith("01") && "91".equals(prefix)) {
            String inquiry = privateValue(message.getString(63), "62");
            if (inquiry == null || !List.of(
                    "24", "25", "26", "27", "29", "30").contains(inquiry)) {
                throw new ISOException(
                        "DE63 tag 62 has invalid Card Control inquiry type");
            }
        }
        if (mti.startsWith("01") && "92".equals(prefix)) {
            if (!message.hasField(31)
                    || privateValue(message.getString(31), "11") == null) {
                throw new ISOException(
                        "DE31 tag 11 is mandatory for PIN management");
            }
            String pc = privateValue(message.getString(63), "PC");
            if (pc == null || pc.length() < 3 || pc.charAt(2) != '0') {
                throw new ISOException(
                        "DE63 tag PC PIN Change scheme must be 0");
            }
            if (!message.hasField(64)) {
                throw new ISOException("DE64 is mandatory for PIN management");
            }
        }
        if (mti.startsWith("02") && "48".equals(prefix)
                && privateValue(message.getString(63), "60") == null) {
            throw new ISOException("DE63 tag 60 is mandatory for P2P");
        }
        if (((mti.startsWith("01")
                && ("50".equals(prefix) || "59".equals(prefix)))
                || (mti.startsWith("022") && "59".equals(prefix)))
                && privateValue(message.getString(63), "60") == null) {
            throw new ISOException(
                    "DE63 tag 60 is mandatory for payment operation");
        }
        if (mti.startsWith("022") && "102".equals(
                message.hasField(24) ? message.getString(24) : "")
                && !message.hasField(37)) {
            throw new ISOException("DE37 is mandatory for AFD Completion");
        }
        if (mti.startsWith("022") && "02".equals(prefix)
                && "202".equals(message.hasField(24)
                ? message.getString(24) : "")) {
            String tip = privateValue(message.getString(63), "38");
            if (tip == null || !tip.matches("\\d{12}")) {
                throw new ISOException(
                        "DE63 tag 38 is mandatory for Tip Purchase Completion");
            }
        }
    }

    public static boolean containsDe63Tag(String data, String expectedTag) {
        if (data == null) return false;
        int offset = 0;
        while (offset + 5 <= data.length()) {
            int length;
            try {
                length = Integer.parseInt(data.substring(offset, offset + 3));
            } catch (NumberFormatException e) {
                return false;
            }
            int end = offset + 3 + length;
            if (length < 2 || end > data.length()) return false;
            if (expectedTag.equals(data.substring(offset + 3, offset + 5))) return true;
            offset = end;
        }
        return false;
    }

    private static String privateValue(String data, String expectedTag) {
        try {
            return WayPosPrivateData.decode(data).stream()
                    .filter(item -> expectedTag.equals(item.tableId()))
                    .map(WayPosPrivateData.Item::value)
                    .findFirst().orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
