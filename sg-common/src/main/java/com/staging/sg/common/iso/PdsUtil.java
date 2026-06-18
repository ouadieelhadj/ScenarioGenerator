package com.staging.sg.common.iso;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Private Data Subelement (PDS) encoding/decoding.
 * Format Mastercard IPM : [Tag 4 digits][Length 3 digits][Data]
 *   Tag    : 0001-9999
 *   Length : 001-992 (longueur de la donnée)
 *   Data   : valeur
 * Plusieurs PDS s'enchaînent dans un même champ (ex: DE048).
 */
public final class PdsUtil {

    private PdsUtil() {}

    /** Encode un seul PDS : tag (int) + valeur → "ttttlllvalue" */
    public static String encode(int tag, String value) {
        if (value == null) value = "";
        return String.format("%04d%03d%s", tag, value.length(), value);
    }

    /** Concatène plusieurs PDS déjà encodés. */
    public static String concat(String... pds) {
        StringBuilder sb = new StringBuilder();
        for (String p : pds) if (p != null) sb.append(p);
        return sb.toString();
    }

    /**
     * Décode une chaîne de PDS concaténés en map {tag → value}.
     * Tolère une chaîne vide ou null (retourne map vide).
     */
    public static Map<Integer, String> decode(String data) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (data == null) return result;
        int i = 0;
        while (i + 7 <= data.length()) {
            try {
                int tag = Integer.parseInt(data.substring(i, i + 4));
                int len = Integer.parseInt(data.substring(i + 4, i + 7));
                int start = i + 7;
                int end = Math.min(start + len, data.length());
                String value = data.substring(start, end);
                result.put(tag, value);
                i = end;
            } catch (NumberFormatException e) {
                break; // chaîne malformée, on s'arrête
            }
        }
        return result;
    }

    /** Construit le PDS 0501 (Transaction Description) avec un usage code. */
    public static String pds0501UsageCode(String usageCode) {
        return encode(501, usageCode != null ? usageCode : "696");
    }
}
