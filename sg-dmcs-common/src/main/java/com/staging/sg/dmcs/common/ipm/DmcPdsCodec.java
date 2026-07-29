package com.staging.sg.dmcs.common.ipm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codec PDS Mastercard : tag sur quatre positions, longueur sur trois
 * positions et valeur non scindée.
 */
public final class DmcPdsCodec {

    private DmcPdsCodec() {
    }

    public static String encode(int tag, String value) {
        if (tag < 0 || tag > 9999) {
            throw new IllegalArgumentException("PDS tag hors plage: " + tag);
        }
        String data = value == null ? "" : value;
        if (data.length() > 992) {
            throw new IllegalArgumentException("PDS trop long: " + data.length());
        }
        return "%04d%03d%s".formatted(tag, data.length(), data);
    }

    public static String concat(String... encodedPds) {
        StringBuilder result = new StringBuilder();
        if (encodedPds != null) {
            for (String pds : encodedPds) {
                if (pds != null && !pds.isEmpty()) {
                    result.append(pds);
                }
            }
        }
        return result.toString();
    }

    public static Map<Integer, String> decode(String carrier) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (carrier == null || carrier.isEmpty()) {
            return result;
        }
        int offset = 0;
        while (offset < carrier.length()) {
            if (offset + 7 > carrier.length()) {
                throw new IllegalArgumentException("En-tête PDS tronqué à l'offset " + offset);
            }
            int tag = parseNumber(carrier, offset, offset + 4, "tag");
            int length = parseNumber(carrier, offset + 4, offset + 7, "longueur");
            int valueStart = offset + 7;
            int valueEnd = valueStart + length;
            if (valueEnd > carrier.length()) {
                throw new IllegalArgumentException("Valeur PDS " + tag + " tronquée");
            }
            if (result.put(tag, carrier.substring(valueStart, valueEnd)) != null) {
                throw new IllegalArgumentException("PDS dupliqué: " + tag);
            }
            offset = valueEnd;
        }
        return result;
    }

    private static int parseNumber(String value, int start, int end, String label) {
        try {
            return Integer.parseInt(value.substring(start, end));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PDS " + label + " non numérique", exception);
        }
    }
}
