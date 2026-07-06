package com.staging.sg.common.iso;

import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOPackager;
import java.io.IOException;

/**
 * Channel jPOS pour SWAM (HPS PowerCARD HSID).
 *
 * Framing d'une trame :
 *   [longueur : 4 caracteres ASCII] [message packe : ISO...MTI...bitmap...DE]
 *
 * La longueur est celle du RESTE du message (hors les 4 octets de longueur),
 * justifiee a droite, completee par des zeros, en ASCII (ex : "0123").
 *
 * PARAMETRABLE : lengthDigits (defaut 4). Un futur passage a 2 octets se fait
 * en changeant cette valeur, sans refactoring.
 *
 * Le header PowerCARD (les 3 car. 'ISO' + 8 car. d'entete) fait partie du
 * MESSAGE lui-meme (gere au niveau applicatif / packager d'entete), PAS du
 * framing de longueur. Ce channel ne gere que le prefixe de longueur ASCII.
 */
public class SwamLengthChannel extends BaseChannel {

    /** Nombre de caracteres ASCII du prefixe de longueur (defaut 4, parametrable). */
    private int lengthDigits = 4;

    public SwamLengthChannel() { super(); }

    public SwamLengthChannel(ISOPackager p) {
        super();
        setPackager(p);
    }

    public void setLengthDigits(int n) { this.lengthDigits = n; }
    public int getLengthDigits() { return lengthDigits; }

    /** Ecrit la longueur sur lengthDigits caracteres ASCII, justifiee droite, zeros a gauche. */
    @Override
    protected void sendMessageLength(int len) throws IOException {
        String s = String.format("%0" + lengthDigits + "d", len);
        if (s.length() > lengthDigits) {
            // longueur trop grande pour le prefixe : on tronque aux N derniers chiffres
            s = s.substring(s.length() - lengthDigits);
        }
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        serverOut.write(b);
    }

    /** Lit lengthDigits caracteres ASCII et les interprete comme un entier decimal. */
    @Override
    protected int getMessageLength() throws IOException {
        byte[] b = new byte[lengthDigits];
        int read = 0;
        while (read < lengthDigits) {
            int r = serverIn.read(b, read, lengthDigits - read);
            if (r < 0) throw new java.io.EOFException("Flux ferme pendant lecture longueur SWAM");
            read += r;
        }
        String s = new String(b, java.nio.charset.StandardCharsets.US_ASCII).trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IOException("Longueur SWAM invalide (non numerique) : '" + s + "'");
        }
    }
}
