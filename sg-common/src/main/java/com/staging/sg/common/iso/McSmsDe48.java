package com.staging.sg.common.iso;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DE48 Mastercard Single Message System : suite de subelements
 *
 *     ID(2 car) + Longueur(2 car) + Valeur
 *
 * Exemple reel (trace simulateur Mastercard, 0800 DE70=161) :
 *
 *     1154PK0000E02B0E8BD4644E6341182D71F4F3F5B543A1____________
 *     ^^ ^^ +-- valeur de 54 caracteres
 *     |  +----- longueur = 54
 *     +-------- subelement 11 (Key Exchange Data Block)
 *
 * ATTENTION : la longueur est sur 2 positions, contrairement au DE48 SWAM
 * (SwamDe48) qui utilise Tag(3) + Longueur(3).
 *
 * --------------------------------------------------------------------
 *  SUBELEMENT 11 — Key Exchange Data Block
 * --------------------------------------------------------------------
 * Deux formats, distingues par la longueur totale :
 *
 *  an-54 (cles DOUBLE longueur)          an-70 (cles TRIPLE longueur)
 *    SF1 Key Class ID       an-2           SF1 Key Class ID       an-2
 *    SF2 Key Index Number   n-2            SF2 Key Index Number   n-2
 *    SF3 Key Cycle Number   n-2            SF3 Key Cycle Number   n-2
 *    SF4 Cle chiffree       an-32          SF4 Cle chiffree       an-48
 *    SF5 Key Check Value    an-16          SF5 Key Check Value    an-16
 *
 * Le KCV (SF5) contient les 4 premiers caracteres hexadecimaux de la valeur
 * calculee, suivis d'espaces (guide p.36330, confirme par la trace : "43A1"
 * + 12 espaces).
 *
 * an-38 : variante observee dans le 0820 (Key Exchange Acknowledgement) ou
 * SF4 et SF5 sont vides — 2+2+2+16+16 = 38.
 *
 * La cle est chiffree sous la ZMK en 3DES-ECB (verifie contre la trace :
 * ZMK 13AED5DA1F32347523C708C11F2608FD, clair BC4AEA2F5BB3FD1504624F8623835D5B
 * -> E02B0E8BD4644E6341182D71F4F3F5B5).
 */
public class McSmsDe48 {

    /** Subelement 11 : Key Exchange Data Block. */
    public static final String SE_KEY_EXCHANGE = "11";

    /** Key Class ID : PIN Key (seule valeur documentee pour le SMS). */
    public static final String KEY_CLASS_PIN = "PK";

    /** Longueurs du subelement 11 selon le format. */
    public static final int LEN_DOUBLE = 54;   // cle 32 hex (16 octets)
    public static final int LEN_TRIPLE = 70;   // cle 48 hex (24 octets)
    public static final int LEN_ACK    = 38;   // 0820 : cle et KCV vides

    private final Map<String, String> subelements = new LinkedHashMap<>();

    // ====================================================================
    //  ACCES GENERIQUE AUX SUBELEMENTS
    // ====================================================================

    public McSmsDe48 put(String id, String value) {
        subelements.put(id, value == null ? "" : value);
        return this;
    }

    public String get(String id)        { return subelements.get(id); }
    public boolean has(String id)       { return subelements.containsKey(id); }
    public Map<String, String> all()    { return subelements; }

    /** Serialise : ID(2) + Longueur(2, zero-padded) + Valeur, concatenes. */
    public String build() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : subelements.entrySet()) {
            String v = e.getValue();
            sb.append(e.getKey());
            sb.append(String.format("%02d", v.length()));
            sb.append(v);
        }
        return sb.toString();
    }

    /** Parse une chaine DE48. Tolerant : s'arrete proprement si tronquee. */
    public static McSmsDe48 parse(String raw) {
        McSmsDe48 d = new McSmsDe48();
        if (raw == null) return d;
        int i = 0, n = raw.length();
        while (i + 4 <= n) {
            String id = raw.substring(i, i + 2);
            int len;
            try { len = Integer.parseInt(raw.substring(i + 2, i + 4)); }
            catch (NumberFormatException ex) { break; }
            int start = i + 4, end = start + len;
            if (end > n) break;
            d.subelements.put(id, raw.substring(start, end));
            i = end;
        }
        return d;
    }

    // ====================================================================
    //  SUBELEMENT 11 — KEY EXCHANGE DATA BLOCK
    // ====================================================================

    /** Contenu decode du subelement 11. */
    public static class KeyExchangeBlock {
        public String keyClassId;      // SF1 : "PK"
        public String keyIndexNumber;  // SF2 : "00"
        public String keyCycleNumber;  // SF3 : "00".."99"
        public String encryptedKey;    // SF4 : cle chiffree sous ZMK (32 ou 48 hex)
        public String keyCheckValue;   // SF5 : 4 car. hex + espaces
        public int    rawLength;       // 54, 70 ou 38

        /** true si la cle est double longueur (16 octets). */
        public boolean isDoubleLength() { return rawLength == LEN_DOUBLE; }

        /** true si la cle est triple longueur (24 octets). */
        public boolean isTripleLength() { return rawLength == LEN_TRIPLE; }

        /** true si c'est la variante d'acquittement (0820) : pas de cle. */
        public boolean isAcknowledgement() {
            return rawLength == LEN_ACK
                || encryptedKey == null
                || encryptedKey.isBlank();
        }

        /** Longueur de la cle en octets, deduite du format. */
        public int keyLengthBytes() {
            if (encryptedKey == null) return 0;
            return encryptedKey.trim().length() / 2;
        }

        /** KCV sans le padding d'espaces. */
        public String kcvTrimmed() {
            return keyCheckValue == null ? null : keyCheckValue.trim();
        }

        @Override
        public String toString() {
            return "KeyExchangeBlock{class=" + keyClassId
                 + ", index=" + keyIndexNumber
                 + ", cycle=" + keyCycleNumber
                 + ", keyLen=" + keyLengthBytes() + "o"
                 + ", kcv=" + kcvTrimmed() + "}";
        }
    }

    /**
     * Decode le subelement 11 present dans ce DE48.
     * @return null si le subelement 11 est absent ou trop court.
     */
    public KeyExchangeBlock keyExchangeBlock() {
        String raw = subelements.get(SE_KEY_EXCHANGE);
        return parseKeyExchangeBlock(raw);
    }

    /** Decode une valeur brute de subelement 11. */
    public static KeyExchangeBlock parseKeyExchangeBlock(String raw) {
        if (raw == null || raw.length() < 6) return null;

        KeyExchangeBlock b = new KeyExchangeBlock();
        b.rawLength      = raw.length();
        b.keyClassId     = raw.substring(0, 2);
        b.keyIndexNumber = raw.substring(2, 4);
        b.keyCycleNumber = raw.substring(4, 6);

        // Longueur de SF4 selon le format total
        int keyLen;
        if (raw.length() >= LEN_TRIPLE)      keyLen = 48;
        else if (raw.length() >= LEN_DOUBLE) keyLen = 32;
        else                                 keyLen = raw.length() - 6 - 16;  // ack

        if (keyLen < 0) keyLen = 0;

        int keyEnd = Math.min(6 + keyLen, raw.length());
        b.encryptedKey  = raw.substring(6, keyEnd);
        b.keyCheckValue = keyEnd < raw.length() ? raw.substring(keyEnd) : "";
        return b;
    }

    /**
     * Construit un subelement 11 complet (livraison de cle, 0800 DE70=161).
     *
     * @param encryptedKeyHex cle chiffree sous ZMK, 32 hex (double) ou 48 (triple)
     * @param kcvHex          KCV calcule ; tronque a 4 caracteres et complete
     *                        par des espaces jusqu'a 16, conformement a la spec
     */
    public McSmsDe48 putKeyExchange(String keyClassId, String keyIndex, String keyCycle,
                                    String encryptedKeyHex, String kcvHex) {
        String kcv4 = (kcvHex == null) ? "" : kcvHex.trim();
        if (kcv4.length() > 4) kcv4 = kcv4.substring(0, 4);
        String kcvPadded = String.format("%-16s", kcv4);

        String v = keyClassId
                 + keyIndex
                 + keyCycle
                 + (encryptedKeyHex == null ? "" : encryptedKeyHex)
                 + kcvPadded;
        return put(SE_KEY_EXCHANGE, v);
    }

    /**
     * Construit un subelement 11 d'acquittement (0820) : classe, index et cycle
     * renseignes, cle et KCV a blanc. Longueur totale 38.
     */
    public McSmsDe48 putKeyExchangeAck(String keyClassId, String keyIndex, String keyCycle) {
        String v = keyClassId + keyIndex + keyCycle + " ".repeat(32);
        return put(SE_KEY_EXCHANGE, v);
    }
}
