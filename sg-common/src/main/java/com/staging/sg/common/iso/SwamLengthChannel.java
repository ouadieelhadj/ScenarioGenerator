package com.staging.sg.common.iso;

import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Channel jPOS pour SWAM (HPS PowerCARD HSID).
 *
 * Structure d'une trame :
 *   [4 octets longueur ASCII] [3 octets 'ISO'] [8 octets header PowerCARD] [MTI + bitmap + DEs]
 *
 * Header PowerCARD (8 octets apres 'ISO') :
 *   Position 1   : produit ('6'=iss, '7'=iss+acq, '8'=acq)
 *   Positions 2-5: version protocole = '0100'
 *   Positions 6-8: premier DE errone ou '000'
 *
 * Cycle jPOS BaseChannel.receive() :
 *   1. getMessageLength() -> lit prefix 4o + header 11o, retourne msgLen
 *   2. streamReceive()    -> lit msgLen octets (NON override, jPOS gere)
 *   3. unpack(m, b)       -> parse le payload (override : log hex + dump ISO)
 *
 * Cycle emission :
 *   send(ISOMsg)          -> override : dump ISO, puis pack + sendMessageLength
 *
 * DUMP ISO : chaque message recu/emis est logge champ par champ, avec le libelle
 * de l'operation (SIGN-ON, KEY EXCHANGE ZPK, AUTORISATION, ...).
 *   - DE2 (PAN) et DE35 (piste 2) sont MASQUES : jamais de PAN en clair en log.
 *   - DE52 (PIN block), DE55 (EMV) et DE128 (MAC) sont affiches en HEX (binaires).
 */
public class SwamLengthChannel extends BaseChannel {

    private static final Logger log = LoggerFactory.getLogger(SwamLengthChannel.class);

    private static final int HEADER_LEN = 11;

    /** Header emis. Produit '6' = interface membre emetteur (le centre repond). */
    private String headerOut = "ISO60100000";

    private int lengthDigits = 4;

    public SwamLengthChannel() { super(); }

    public SwamLengthChannel(ISOPackager p) {
        super();
        setPackager(p);
    }

    public void setLengthDigits(int n) { this.lengthDigits = n; }
    public int getLengthDigits() { return lengthDigits; }

    /** Permet de changer le header emis (ex : "ISO70100000" en mode membre). */
    public void setHeaderOut(String h) { this.headerOut = h; }
    public String getHeaderOut() { return headerOut; }

    // ========================================================================
    //  EMISSION
    // ========================================================================

    /** Override send() pour dumper le message ISO complet AVANT le packing. */
    @Override
    public void send(ISOMsg m) throws IOException, ISOException {
        dump(m, ">>> EMIS ");
        super.send(m);
    }

    /**
     * Emis : longueur (prefix 4o ASCII) + header PowerCARD fixe.
     * La longueur inclut les 11 octets du header.
     */
    @Override
    protected void sendMessageLength(int len) throws IOException {
        int totalLen = len + HEADER_LEN;
        String s = String.format("%0" + lengthDigits + "d", totalLen);
        if (s.length() > lengthDigits) {
            s = s.substring(s.length() - lengthDigits);
        }
        byte[] lenBytes = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] hdrBytes = headerOut.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        serverOut.write(lenBytes);
        serverOut.write(hdrBytes);
        log.debug("[SWAM-CHANNEL] Emis prefix=[{}] header=[{}]", s, headerOut);
    }

    // ========================================================================
    //  RECEPTION
    // ========================================================================

    /**
     * Recu : lit le prefix 4o ASCII + header PowerCARD 11o.
     * Retourne msgLen = totalLen - 11 pour que jPOS lise exactement le payload.
     */
    @Override
    protected int getMessageLength() throws IOException {
        // 1. Lire prefix longueur
        byte[] lenBuf = new byte[lengthDigits];
        int read = 0;
        while (read < lengthDigits) {
            int r = serverIn.read(lenBuf, read, lengthDigits - read);
            if (r < 0) throw new java.io.EOFException("Flux ferme pendant lecture longueur SWAM");
            read += r;
        }
        StringBuilder hexLen = new StringBuilder();
        for (byte x : lenBuf) hexLen.append(String.format("%02X ", x));
        String lenStr = new String(lenBuf, java.nio.charset.StandardCharsets.US_ASCII).trim();
        log.info("[SWAM-CHANNEL] Prefix longueur HEX=[{}] valeur='{}'", hexLen.toString().trim(), lenStr);

        int totalLen;
        try {
            totalLen = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            log.error("[SWAM-CHANNEL] Longueur non numerique HEX=[{}]", hexLen.toString().trim());
            throw new IOException("Longueur SWAM invalide : '" + lenStr + "'");
        }

        // 2. Lire header PowerCARD (11 octets)
        byte[] headerBuf = new byte[HEADER_LEN];
        int hread = 0;
        while (hread < HEADER_LEN) {
            int r = serverIn.read(headerBuf, hread, HEADER_LEN - hread);
            if (r < 0) throw new java.io.EOFException("Flux ferme pendant lecture header PowerCARD");
            hread += r;
        }
        String headerStr = new String(headerBuf, java.nio.charset.StandardCharsets.US_ASCII);
        log.info("[SWAM-CHANNEL] Header PowerCARD=[{}] produit='{}' version='{}' deErr='{}'",
                headerStr,
                headerStr.length() > 3  ? headerStr.substring(3, 4)  : "?",
                headerStr.length() > 8  ? headerStr.substring(4, 8)  : "?",
                headerStr.length() >= 11 ? headerStr.substring(8, 11) : "?");

        // 3. Retourner msgLen = totalLen - HEADER_LEN
        int msgLen = totalLen - HEADER_LEN;
        log.info("[SWAM-CHANNEL] Longueur payload (hors header) = {}", msgLen);
        return msgLen;
    }

    /**
     * Override unpack() : log du buffer brut AVANT parsing, puis dump ISO apres.
     * La liaison permanente est preservee car streamReceive() n'est pas touche.
     */
    @Override
    protected void unpack(ISOMsg m, byte[] b) throws ISOException {
        StringBuilder hexMsg = new StringBuilder();
        StringBuilder ascMsg = new StringBuilder();
        for (byte x : b) {
            hexMsg.append(String.format("%02X ", x));
            ascMsg.append((x >= 32 && x < 127) ? (char) x : '.');
        }
        log.info("[SWAM-CHANNEL] ===== PAYLOAD BRUT AVANT PARSING ({} octets) =====", b.length);
        log.info("[SWAM-CHANNEL] HEX : {}", hexMsg.toString().trim());
        log.info("[SWAM-CHANNEL] ASC : {}", ascMsg.toString());
        log.info("[SWAM-CHANNEL] ================================================");

        try {
            super.unpack(m, b);
        } catch (Exception e) {
            log.error("[SWAM-CHANNEL] ERREUR PARSING : {} — {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new ISOException("Parsing failed: " + e.getMessage(), e);
        }
        dump(m, "<<< RECU ");
    }

    // ========================================================================
    //  DUMP ISO
    // ========================================================================

    /** Dump lisible du message : MTI, libelle de l'operation, puis chaque DE. */
    private void dump(ISOMsg m, String sens) {
        try {
            String mti  = m.getMTI();
            String de24 = m.hasField(24) ? m.getString(24) : null;
            String de39 = m.hasField(39) ? m.getString(39) : null;

            StringBuilder sb = new StringBuilder();
            sb.append("\n[SWAM-DUMP] ").append(sens).append(' ')
              .append(mti);
            if (de24 != null) sb.append('/').append(de24);
            sb.append("  ").append(label(mti, de24));
            if (de39 != null) sb.append("  DE39=").append(de39);
            sb.append('\n');

            int max = m.getMaxField();
            for (int i = 1; i <= max; i++) {
                if (!m.hasField(i)) continue;
                String val = fieldValue(m, i);
                if (val == null) continue;
                sb.append(String.format("  DE%03d (%3d) = %s%n", i, rawLen(m, i), val));
            }
            log.info(sb.toString());
        } catch (Exception e) {
            log.warn("[SWAM-DUMP] dump impossible : {}", e.getMessage());
        }
    }

    /** Valeur affichable d'un champ : hex pour les binaires, masquee pour les PAN. */
    private String fieldValue(ISOMsg m, int i) {
        try {
            // Champs binaires -> hex
            if (i == 52 || i == 55 || i == 64 || i == 128) {
                byte[] b = m.getBytes(i);
                return (b == null) ? null : ISOUtil.hexString(b) + "   [hex]";
            }
            String s = m.getString(i);
            if (s == null) return null;
            // Donnees porteur -> masquees
            if (i == 2 || i == 35 || i == 45) return mask(s) + "   [masque]";
            return s;
        } catch (Exception e) {
            return "<illisible>";
        }
    }

    private int rawLen(ISOMsg m, int i) {
        try {
            if (i == 52 || i == 55 || i == 64 || i == 128) {
                byte[] b = m.getBytes(i);
                return (b == null) ? 0 : b.length;
            }
            String s = m.getString(i);
            return (s == null) ? 0 : s.length();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Masque un PAN / une piste : 6 premiers + 4 derniers. */
    private String mask(String s) {
        if (s == null) return null;
        if (s.length() < 11) return "****";
        return s.substring(0, 6) + "*".repeat(s.length() - 10) + s.substring(s.length() - 4);
    }

    /** Libelle de l'operation deduit du MTI et du DE24. */
    private String label(String mti, String de24) {
        if (mti == null) return "?";
        switch (mti) {
            case "1100": return "AUTORISATION (achat)";
            case "1110": return "REPONSE AUTORISATION";
            case "1200": return "FINANCIER";
            case "1210": return "REPONSE FINANCIER";
            case "1420": return "ANNULATION";
            case "1430": return "REPONSE ANNULATION";
            case "1804": return "GESTION RESEAU — " + funcLabel(de24);
            case "1814": return "REPONSE GESTION RESEAU — " + funcLabel(de24);
            default:     return "MTI " + mti;
        }
    }

    private String funcLabel(String de24) {
        if (de24 == null) return "fonction inconnue";
        switch (de24) {
            case "801": return "SIGN-ON";
            case "802": return "SIGN-OFF";
            case "803": return "ECHO-TEST";
            case "811": return "KEY EXCHANGE ZPK";
            case "899": return "KEY EXCHANGE ZAK";
            default:    return "FONCTION " + de24;
        }
    }
}
