package com.staging.sg.common.iso;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace detaillee d'un message ISO, facon PowerCARD :
 * buffer hexa complet, MTI, puis chaque champ sous la forme
 *   FLD (nnn) : (longueur) : [valeur]
 *
 * Le DE55 est en outre decode tag par tag (TLV EMV), avec le nom
 * de chaque tag — indispensable pour comparer avec le reseau.
 *
 * Utilise des DEUX cotes (membre et mastercard) a chaque envoi et
 * chaque reception.
 */
public final class IsoDump {

    private static final Logger log = LoggerFactory.getLogger("ISO-DUMP");

    private IsoDump() {}

    /** Libelles des tags EMV rencontres dans le DE55. */
    private static final Map<String, String> TAGS = new LinkedHashMap<>();
    static {
        TAGS.put("9F26", "Application Cryptogram (ARQC)");
        TAGS.put("9F27", "Cryptogram Information Data");
        TAGS.put("9F10", "Issuer Application Data (IAD)");
        TAGS.put("9F36", "Application Transaction Counter (ATC)");
        TAGS.put("9F37", "Unpredictable Number");
        TAGS.put("9F33", "Terminal Capabilities");
        TAGS.put("9F34", "CVM Results");
        TAGS.put("9F35", "Terminal Type");
        TAGS.put("9F1A", "Terminal Country Code");
        TAGS.put("9F02", "Amount, Authorised");
        TAGS.put("9F03", "Amount, Other");
        TAGS.put("9F09", "Application Version Number");
        TAGS.put("9F41", "Transaction Sequence Counter");
        TAGS.put("9F53", "Transaction Category Code");
        TAGS.put("9F1E", "Terminal Serial Number");
        TAGS.put("5F2A", "Transaction Currency Code");
        TAGS.put("5F34", "PAN Sequence Number");
        TAGS.put("95",   "Terminal Verification Results (TVR)");
        TAGS.put("9A",   "Transaction Date");
        TAGS.put("9C",   "Transaction Type");
        TAGS.put("82",   "Application Interchange Profile (AIP)");
        TAGS.put("84",   "Dedicated File Name (AID)");
        TAGS.put("91",   "Issuer Authentication Data (ARPC)");
        TAGS.put("71",   "Issuer Script Template 1");
        TAGS.put("72",   "Issuer Script Template 2");
    }

    /** @param sens "ENVOI" ou "RECEPTION" ; @param who ex. "MEMBRE" */
    public static void dump(String who, String sens, ISOMsg m) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\n------------------------------------\n");
            sb.append("- ").append(who).append("  ").append(sens).append("\n");
            sb.append("------------------------------------\n");

            byte[] de55 = null;
            try {
                byte[] raw = m.pack();
                sb.append("- BUFFER HEX : ").append(ISOUtil.hexString(raw)).append("\n");
            } catch (Exception e) {
                sb.append("- BUFFER HEX : <pack impossible : ").append(e.getMessage()).append(">\n");
            }

            sb.append("------------------------------------\n");
            sb.append("- M.T.I      : ").append(safeMti(m)).append("\n");
            sb.append("------------------------------------\n");
            sb.append("- FLD (FIELD): LENGTH :  DATA\n");
            sb.append("------------------------------------\n");

            int max = m.getMaxField();
            for (int i = 1; i <= max; i++) {
                if (!m.hasField(i)) continue;
                String val;
                int len;
                try {
                    Object v = m.getComponent(i) != null ? m.getComponent(i).getValue() : null;
                    if (v instanceof byte[] b) {
                        val = ISOUtil.hexString(b);
                        len = b.length;
                        if (i == 55) de55 = b;
                    } else {
                        val = m.getString(i);
                        len = val != null ? val.length() : 0;
                    }
                } catch (Exception e) {
                    val = "<illisible>";
                    len = 0;
                }
                sb.append(String.format("- FLD (%03d) : (%03d) : [%s]%n", i, len, val));
            }
            sb.append("------------------------------------");

            // Detail du DE55, tag par tag
            if (de55 != null && de55.length > 0) {
                sb.append("\n- DE55 : detail des tags EMV\n");
                sb.append("------------------------------------\n");
                sb.append(decodeTlv(de55));
                sb.append("------------------------------------");
            }
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[ISO-DUMP] echec du dump : {}", e.getMessage());
        }
    }

    /** Decode un buffer TLV EMV en lignes lisibles. */
    public static String decodeTlv(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < data.length) {
            int tagStart = i;
            int b0 = data[i] & 0xFF;
            i++;
            // tag sur 2 octets si les 5 bits de poids faible valent 11111
            if ((b0 & 0x1F) == 0x1F && i < data.length) i++;
            String tag = ISOUtil.hexString(data, tagStart, i - tagStart).toUpperCase();

            if (i >= data.length) {
                sb.append(String.format("  %-6s <longueur absente>%n", tag));
                break;
            }
            int len = data[i] & 0xFF;
            i++;
            if (i + len > data.length) {
                sb.append(String.format("  %-6s (%02d) <depasse la fin du buffer>%n", tag, len));
                break;
            }
            String val = ISOUtil.hexString(data, i, len).toUpperCase();
            i += len;
            String name = TAGS.getOrDefault(tag, "");
            sb.append(String.format("  %-6s (%02d) %-40s %s%n", tag, len, val, name));
        }
        return sb.toString();
    }

    private static String safeMti(ISOMsg m) {
        try { return m.getMTI(); } catch (Exception e) { return "????"; }
    }
}
