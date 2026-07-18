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
 * Channel jPOS pour Mastercard Single Message System (SMS).
 *
 * Structure d'une trame (SUPPOSEE, a confirmer avec le MIP reel) :
 *   [2 octets longueur BINAIRE big-endian] [MTI + bitmap + DEs]
 *
 * Contrairement a SWAM/PowerCARD, il n'y a PAS de header applicatif ISOxxxx.
 * La longueur est binaire (pas ASCII) et ne couvre QUE le payload.
 *
 * A CONFIRMER : la specification exacte du framing est dans le
 * *Secured Data Communications Guide* de Mastercard (doc separee, non
 * disponible). Si le MIP reel utilise 4 octets ou un header supplementaire,
 * seules getMessageLength() et sendMessageLength() sont a adapter.
 *
 * Cycle jPOS BaseChannel.receive() :
 *   1. getMessageLength() -> lit le prefixe 2o, retourne msgLen
 *   2. streamReceive()    -> lit msgLen octets (NON override, jPOS gere)
 *   3. unpack(m, b)       -> parse le payload (override : log hex + dump ISO)
 *
 * DUMP ISO : chaque message recu/emis est logge champ par champ.
 *   - DE2 (PAN), DE35 (piste 2), DE45 (piste 1) sont MASQUES.
 *   - DE52 (PIN block), DE55 (EMV), DE96 (Message Security Code) en HEX.
 */
public class McSmsLengthChannel extends BaseChannel {

    private static final Logger log = LoggerFactory.getLogger(McSmsLengthChannel.class);

    /** Nombre d'octets du prefixe de longueur. 2 par defaut (big-endian binaire). */
    private int lengthBytes = 2;

    /** true = la longueur annoncee inclut les octets du prefixe lui-meme. */
    private boolean lengthIncludesPrefix = false;

    public McSmsLengthChannel() { super(); }

    public McSmsLengthChannel(ISOPackager p) {
        super();
        setPackager(p);
    }

    public void setLengthBytes(int n) { this.lengthBytes = n; }
    public int  getLengthBytes()      { return lengthBytes; }

    public void setLengthIncludesPrefix(boolean b) { this.lengthIncludesPrefix = b; }
    public boolean isLengthIncludesPrefix()        { return lengthIncludesPrefix; }

    // ========================================================================
    //  EMISSION
    // ========================================================================

    @Override
    public void send(ISOMsg m) throws IOException, ISOException {
        dump(m, ">>> EMIS ");
        super.send(m);
    }

    /** Prefixe de longueur binaire big-endian sur lengthBytes octets. */
    @Override
    protected void sendMessageLength(int len) throws IOException {
        int total = lengthIncludesPrefix ? (len + lengthBytes) : len;
        byte[] prefix = new byte[lengthBytes];
        for (int i = lengthBytes - 1; i >= 0; i--) {
            prefix[i] = (byte) (total & 0xFF);
            total >>>= 8;
        }
        serverOut.write(prefix);
        log.debug("[MC-CHANNEL] Emis prefix={} (len={})", ISOUtil.hexString(prefix), len);
    }

    // ========================================================================
    //  RECEPTION
    // ========================================================================

    @Override
    protected int getMessageLength() throws IOException {
        byte[] buf = new byte[lengthBytes];
        int read = 0;
        while (read < lengthBytes) {
            int r = serverIn.read(buf, read, lengthBytes - read);
            if (r < 0) throw new java.io.EOFException("Flux ferme pendant lecture longueur MC SMS");
            read += r;
        }

        int total = 0;
        for (byte b : buf) total = (total << 8) | (b & 0xFF);

        int msgLen = lengthIncludesPrefix ? (total - lengthBytes) : total;
        log.info("[MC-CHANNEL] Prefix HEX=[{}] -> payload {} octets",
                ISOUtil.hexString(buf), msgLen);

        if (msgLen < 0 || msgLen > 32000) {
            throw new IOException("Longueur MC SMS aberrante : " + msgLen
                    + " (prefix=" + ISOUtil.hexString(buf) + ")");
        }
        return msgLen;
    }

    @Override
    protected void unpack(ISOMsg m, byte[] b) throws ISOException {
        StringBuilder hex = new StringBuilder();
        StringBuilder asc = new StringBuilder();
        for (byte x : b) {
            hex.append(String.format("%02X ", x));
            asc.append((x >= 32 && x < 127) ? (char) x : '.');
        }
        log.info("[MC-CHANNEL] ===== PAYLOAD BRUT AVANT PARSING ({} octets) =====", b.length);
        log.info("[MC-CHANNEL] HEX : {}", hex.toString().trim());
        log.info("[MC-CHANNEL] ASC : {}", asc.toString());
        log.info("[MC-CHANNEL] ================================================");

        try {
            super.unpack(m, b);
        } catch (Exception e) {
            log.error("[MC-CHANNEL] ERREUR PARSING : {} — {}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new ISOException("Parsing failed: " + e.getMessage(), e);
        }
        dump(m, "<<< RECU ");
    }

    // ========================================================================
    //  DUMP ISO
    // ========================================================================

    private void dump(ISOMsg m, String sens) {
        try {
            String mti  = m.getMTI();
            String de70 = m.hasField(70) ? m.getString(70) : null;
            String de39 = m.hasField(39) ? m.getString(39) : null;

            StringBuilder sb = new StringBuilder();
            sb.append("\n[MC-DUMP] ").append(sens).append(' ').append(mti);
            if (de70 != null) sb.append('/').append(de70);
            sb.append("  ").append(label(mti, de70));
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
            log.warn("[MC-DUMP] dump impossible : {}", e.getMessage());
        }
    }

    private String fieldValue(ISOMsg m, int i) {
        try {
            if (i == 52 || i == 55 || i == 96) {
                byte[] b = m.getBytes(i);
                return (b == null) ? null : ISOUtil.hexString(b) + "   [hex]";
            }
            String s = m.getString(i);
            if (s == null) return null;
            if (i == 2 || i == 35 || i == 45) return mask(s) + "   [masque]";
            return s;
        } catch (Exception e) {
            return "<illisible>";
        }
    }

    private int rawLen(ISOMsg m, int i) {
        try {
            if (i == 52 || i == 55 || i == 96) {
                byte[] b = m.getBytes(i);
                return (b == null) ? 0 : b.length;
            }
            String s = m.getString(i);
            return (s == null) ? 0 : s.length();
        } catch (Exception e) {
            return 0;
        }
    }

    private String mask(String s) {
        if (s == null) return null;
        if (s.length() < 11) return "****";
        return s.substring(0, 6) + "*".repeat(s.length() - 10) + s.substring(s.length() - 4);
    }

    /** Libelle deduit du MTI et du DE70 (Network Management Information Code). */
    private String label(String mti, String de70) {
        if (mti == null) return "?";
        switch (mti) {
            case "0200": return "FINANCIAL TRANSACTION REQUEST";
            case "0210": return "REPONSE FINANCIAL TRANSACTION";
            case "0220": return "FINANCIAL TRANSACTION ADVICE";
            case "0230": return "REPONSE ADVICE";
            case "0420": return "ACQUIRER REVERSAL ADVICE";
            case "0430": return "REPONSE REVERSAL";
            case "0800": return "NETWORK MANAGEMENT REQUEST — " + funcLabel(de70);
            case "0810": return "REPONSE NETWORK MANAGEMENT — " + funcLabel(de70);
            default:     return "MTI " + mti;
        }
    }

    /** Codes DE70 du Single Message System. */
    private String funcLabel(String de70) {
        if (de70 == null) return "fonction inconnue";
        switch (de70) {
            case "061": return "SIGN-ON";
            case "062": return "SIGN-OFF";
            case "161": return "PEK EXCHANGE";
            case "270": return "ECHO TEST";
            case "301": return "CUTOVER";
            default:    return "FONCTION " + de70;
        }
    }
}
