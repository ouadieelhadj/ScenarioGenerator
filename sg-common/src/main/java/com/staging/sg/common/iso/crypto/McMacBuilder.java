package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Construit la donnée à MACer (MAC input) à partir d'un ISOMsg,
 * selon une liste de champs et une représentation, de façon
 * DÉTERMINISTE et IDENTIQUE côté acquéreur et émetteur.
 *
 * representation :
 *   "ascii"  -> concat des valeurs getString(f) en ASCII brut
 *   "ebcdic" -> concat des valeurs getString(f) encodées en EBCDIC (Cp1047)
 *
 * Les champs absents sont ignorés (pas d'octet ajouté). L'ordre suit
 * exactement la liste fournie (fieldsCsv), pas l'ordre du bitmap.
 */
public final class McMacBuilder {

    private static final Logger log = LoggerFactory.getLogger(McMacBuilder.class);
    private static final Charset EBCDIC = Charset.forName("Cp1047");

    private McMacBuilder() {}

    /**
     * @param msg          le message ISO
     * @param fieldsCsv    ex "4,11,37,41,42" (ordre significatif)
     * @param representation "ascii" ou "ebcdic"
     * @return les octets à MACer
     */
    public static byte[] build(ISOMsg msg, String fieldsCsv, String representation) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        boolean ebcdic = "ebcdic".equalsIgnoreCase(representation);

        StringBuilder trace = new StringBuilder();
        for (String part : fieldsCsv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            int f = Integer.parseInt(t);
            if (!msg.hasField(f)) continue;
            String val = msg.getString(f);
            if (val == null) continue;
            byte[] b = ebcdic ? val.getBytes(EBCDIC) : val.getBytes("ISO-8859-1");
            bos.write(b);
            trace.append(f).append("=").append(val).append(" ");
        }

        byte[] result = bos.toByteArray();
        log.info("[MAC-BUILD] repr={} fields=[{}] inputLen={} inputHex={}",
                representation, trace.toString().trim(), result.length, ISOUtil.hexString(result));
        return result;
    }
}
