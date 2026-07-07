package com.staging.sg.common.iso;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DE48 SWAM (HPS HSID) : structure TLV = Tag(3 car) + Longueur(3 car) + Valeur.
 * Tags utilises pour le key exchange (spec + decisions section 16) :
 *   P16 (032) = ZPK chiffree sous KEK (32 hex)   ;  K16 (006) = KCV ZPK (tag maison)
 *   P10 (016) = ZAK chiffree sous KEK (16 hex)   ;  K10 (006) = KCV ZAK (tag maison)
 * Encodage tout-ASCII, coherent avec SwamPackager (DE48 = IFA_LLLCHAR).
 */
public class SwamDe48 {

    public static final String TAG_ZPK = "P16";
    public static final String TAG_ZAK = "P10";
    public static final String TAG_ZPK_KCV = "K16";
    public static final String TAG_ZAK_KCV = "K10";

    private final Map<String,String> tags = new LinkedHashMap<>();

    public SwamDe48 put(String tag, String value) {
        tags.put(tag, value == null ? "" : value);
        return this;
    }
    public String get(String tag) { return tags.get(tag); }
    public boolean has(String tag) { return tags.containsKey(tag); }
    public Map<String,String> tags() { return tags; }

    /** Serialise en Tag(3)+Long(3 zero-padded)+Valeur, concatenes. */
    public String build() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String,String> e : tags.entrySet()) {
            String v = e.getValue();
            sb.append(e.getKey());
            sb.append(String.format("%03d", v.length()));
            sb.append(v);
        }
        return sb.toString();
    }

    /** Parse une chaine DE48 TLV. Tolerant : s'arrete proprement si tronque. */
    public static SwamDe48 parse(String raw) {
        SwamDe48 d = new SwamDe48();
        if (raw == null) return d;
        int i = 0, n = raw.length();
        while (i + 6 <= n) {
            String tag = raw.substring(i, i + 3);
            int len;
            try { len = Integer.parseInt(raw.substring(i + 3, i + 6)); }
            catch (NumberFormatException ex) { break; }
            int start = i + 6, end = start + len;
            if (end > n) break;
            d.tags.put(tag, raw.substring(start, end));
            i = end;
        }
        return d;
    }
}
