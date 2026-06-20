package com.staging.sg.common.iso.crypto;

/**
 * DE 48 subelement 11 — Key Exchange Block Data (DMAS officiel).
 *
 * Structure (triple-length, clés 24 octets) :
 *   [Subelement ID "11"][Length 2pos][Subfields 1-5]
 *   Subfield 1 (Key Class ID)    an-2  : "PK" (PIN key)
 *   Subfield 2 (Key Index)       n-2   : "00"
 *   Subfield 3 (Key Cycle)       n-2   : "00"-"99"
 *   Subfield 4 (Encrypted Key)   an-48 : PEK chiffré sous KEK (hex)
 *   Subfield 5 (Key Check Value) an-16 : KCV (hex)
 *
 * Le contenu des subfields 1-5 (triple) = 2+2+2+48+16 = 70 caractères (an-70).
 * Précédé de "11" + longueur "70" dans le DE48.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeyExchangeBlock {

    private static final Logger log = LoggerFactory.getLogger(KeyExchangeBlock.class);

    public static final String SUBELEMENT_ID = "11";
    public static final String KEY_CLASS_PIN = "PK";

    public String keyClassId  = KEY_CLASS_PIN; // PK
    public String keyIndex    = "00";
    public String keyCycle    = "00";
    public String encryptedKeyHex;             // PEK sous KEK (hex)
    public String kcv;                         // KCV (hex)

    /** Construit le contenu des subfields (sans l'ID/length du subelement). */
    public String buildSubfields() {
        // KCV sur 16 hex : padder si besoin (notre KCV fait 6 hex -> compléter)
        String kcv16 = padRight(kcv == null ? "" : kcv, 16);
        return keyClassId + keyIndex + keyCycle + encryptedKeyHex + kcv16;
    }

    /** Construit le DE48 complet : ID subelement + longueur + subfields. */
    public String buildDe48() {
        String sub = buildSubfields();
        return SUBELEMENT_ID + String.format("%02d", sub.length()) + sub;
    }

    /** Parse un DE48 (ID subelement + longueur + subfields) en KeyExchangeBlock. */
    public static KeyExchangeBlock parseDe48(String de48) {
        // de48 = "11" + LL + subfields
        String id = de48.substring(0, 2);
        if (!SUBELEMENT_ID.equals(id))
            throw new IllegalArgumentException("Subelement ID attendu 11, recu " + id);
        int len = Integer.parseInt(de48.substring(2, 4));
        String sub = de48.substring(4, 4 + len);
        return parseSubfields(sub);
    }

    /** Parse les subfields seuls (triple-length an-70). */
    public static KeyExchangeBlock parseSubfields(String sub) {
        KeyExchangeBlock b = new KeyExchangeBlock();
        b.keyClassId = sub.substring(0, 2);
        b.keyIndex   = sub.substring(2, 4);
        b.keyCycle   = sub.substring(4, 6);
        // triple-length : clé = 48 hex (positions 7-54), KCV = 16 hex (55-70)
        int keyHexLen = sub.length() - 6 - 16; // reste après header(6) et kcv(16)
        b.encryptedKeyHex = sub.substring(6, 6 + keyHexLen);
        b.kcv = sub.substring(6 + keyHexLen);
        return b;
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append('0');
        return sb.toString();
    }

    /** Log détaillé champ par champ (traçabilité DMAS). */
    public void logDetail(String contexte) {
        log.info("[DE48-KEB] {} — Subelement ID=11", contexte);
        log.info("[DE48-KEB]   SF1 Key Class ID  = {}", keyClassId);
        log.info("[DE48-KEB]   SF2 Key Index     = {}", keyIndex);
        log.info("[DE48-KEB]   SF3 Key Cycle     = {}", keyCycle);
        log.info("[DE48-KEB]   SF4 Encrypted Key = {}", encryptedKeyHex);
        log.info("[DE48-KEB]   SF5 KCV           = {}", kcv);
    }

    @Override
    public String toString() {
        return "KEB{class=" + keyClassId + " idx=" + keyIndex + " cycle=" + keyCycle
             + " key=" + encryptedKeyHex + " kcv=" + kcv + "}";
    }
}
