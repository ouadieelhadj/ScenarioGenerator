package com.staging.sg.common.iso;

import org.jpos.iso.BaseChannel;
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
 * La longueur (4 octets ASCII) couvre le reste du message
 * y compris le header 11 octets (ISO + 8).
 *
 * PARAMETRABLE : lengthDigits (defaut 4).
 */
public class SwamLengthChannel extends BaseChannel {

    private static final Logger log = LoggerFactory.getLogger(SwamLengthChannel.class);

    /** Header fixe : 3 octets 'ISO' + 8 octets PowerCARD = 11 octets total */
    private static final int HEADER_LEN = 11;

    /** Header emis vers le membre : ISO + produit '6' + version '0100' + '000' */
    private static final byte[] HEADER_OUT = "ISO60100000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** Nombre de caracteres ASCII du prefixe de longueur (defaut 4, parametrable). */
    private int lengthDigits = 4;

    public SwamLengthChannel() { super(); }

    public SwamLengthChannel(ISOPackager p) {
        super();
        setPackager(p);
    }

    public void setLengthDigits(int n) { this.lengthDigits = n; }
    public int getLengthDigits() { return lengthDigits; }

    /**
     * Ecrit la longueur sur lengthDigits caracteres ASCII (inclut le header 11 octets),
     * puis ecrit le header PowerCARD fixe.
     */
    @Override
    protected void sendMessageLength(int len) throws IOException {
        // La longueur inclut le header 11 octets
        int totalLen = len + HEADER_LEN;
        String s = String.format("%0" + lengthDigits + "d", totalLen);
        if (s.length() > lengthDigits) {
            s = s.substring(s.length() - lengthDigits);
        }
        byte[] lenBytes = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        serverOut.write(lenBytes);
        // Ecrire le header PowerCARD
        serverOut.write(HEADER_OUT);
        log.debug("[SWAM-CHANNEL] Emis prefix=[{}] header=[{}]", s, new String(HEADER_OUT));
    }

    /**
     * Lit le prefixe de longueur (4 octets ASCII), puis lit et logge
     * le header PowerCARD de 11 octets. Retourne la longueur du message
     * restant (hors header).
     */
    @Override
    protected int getMessageLength() throws IOException {
        // Lecture prefixe longueur
        byte[] lenBuf = new byte[lengthDigits];
        int read = 0;
        while (read < lengthDigits) {
            int r = serverIn.read(lenBuf, read, lengthDigits - read);
            if (r < 0) throw new java.io.EOFException("Flux ferme pendant lecture longueur SWAM");
            read += r;
        }

        // Log hex du prefixe
        StringBuilder hex = new StringBuilder();
        for (byte x : lenBuf) hex.append(String.format("%02X ", x));
        String lenStr = new String(lenBuf, java.nio.charset.StandardCharsets.US_ASCII).trim();
        log.info("[SWAM-CHANNEL] Prefix longueur recu ({} octets) HEX=[{}] valeur='{}'",
                lengthDigits, hex.toString().trim(), lenStr);

        int totalLen;
        try {
            totalLen = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            log.error("[SWAM-CHANNEL] Longueur non numerique — framing incompatible. HEX=[{}]",
                    hex.toString().trim());
            throw new IOException("Longueur SWAM invalide : '" + lenStr + "' HEX=" + hex.toString().trim());
        }

        // Lecture header PowerCARD (11 octets)
        byte[] headerBuf = new byte[HEADER_LEN];
        int hread = 0;
        while (hread < HEADER_LEN) {
            int r = serverIn.read(headerBuf, hread, HEADER_LEN - hread);
            if (r < 0) throw new java.io.EOFException("Flux ferme pendant lecture header PowerCARD");
            hread += r;
        }
        String headerStr = new String(headerBuf, java.nio.charset.StandardCharsets.US_ASCII);
        log.info("[SWAM-CHANNEL] Header PowerCARD recu ASCII=[{}] produit='{}' version='{}' deErr='{}'",
                headerStr,
                headerStr.length() > 3 ? headerStr.substring(3, 4) : "?",
                headerStr.length() > 8 ? headerStr.substring(4, 8) : "?",
                headerStr.length() >= 11 ? headerStr.substring(8, 11) : "?");

        // Longueur retournee = longueur du message restant (hors header)
        int msgLen = totalLen - HEADER_LEN;
        log.info("[SWAM-CHANNEL] Longueur message (hors header) = {}", msgLen);
        return msgLen;
    }
}
