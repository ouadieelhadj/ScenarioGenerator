package com.staging.sg.common.iso;

import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
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
 *   3. unpack(m, b)       -> parse le payload (override pour log hex avant parsing)
 */
public class SwamLengthChannel extends BaseChannel {

    private static final Logger log = LoggerFactory.getLogger(SwamLengthChannel.class);

    private static final int HEADER_LEN = 11;
    private static final byte[] HEADER_OUT = "ISO60100000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private int lengthDigits = 4;

    public SwamLengthChannel() { super(); }

    public SwamLengthChannel(ISOPackager p) {
        super();
        setPackager(p);
    }

    public void setLengthDigits(int n) { this.lengthDigits = n; }
    public int getLengthDigits() { return lengthDigits; }

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
        serverOut.write(lenBytes);
        serverOut.write(HEADER_OUT);
        log.debug("[SWAM-CHANNEL] Emis prefix=[{}] header=[{}]", s, new String(HEADER_OUT));
    }

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
        // jPOS va lire exactement msgLen octets via streamReceive() (non override)
        int msgLen = totalLen - HEADER_LEN;
        log.info("[SWAM-CHANNEL] Longueur payload (hors header) = {}", msgLen);
        return msgLen;
    }

    /**
     * Override unpack() pour logger le buffer brut AVANT parsing.
     * C'est ici qu'on voit exactement ce que le packager recoit.
     * La liaison permanente est preservee car streamReceive() n'est pas touche.
     */
    @Override
    protected void unpack(ISOMsg m, byte[] b) throws ISOException {
        // Log payload complet HEX + ASCII
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

        // Appel normal du packager
        try {
            super.unpack(m, b);
            log.info("[SWAM-CHANNEL] Parsing OK - MTI={}", m.getMTI());
        } catch (Exception e) {
            log.error("[SWAM-CHANNEL] ERREUR PARSING : {} — {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new ISOException("Parsing failed: " + e.getMessage(), e);
        }
    }
}
