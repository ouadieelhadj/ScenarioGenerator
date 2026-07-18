package com.staging.sg.mc.network;

import com.staging.sg.common.iso.MastercardSmsPackager;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.channel.BASE24Channel;
import org.jpos.iso.channel.NACChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.Socket;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client jPOS pour la connexion au reseau Mastercard Single Message System.
 *
 * Gere :
 *   - Connexion TCP au MIP Mastercard
 *   - Framing : prefixe de longueur 2 octets big-endian (a confirmer avec le MIP reel)
 *   - Sign-on  0800 DE70=061 / reponse 0810
 *   - Echo     0800 DE70=270 / reponse 0810
 *   - Sign-off 0800 DE70=062 / reponse 0810
 *
 * NOTE : L'encodage EBCDIC (p.163 du guide) est gere par le packager.
 *        Ce client utilise MastercardSmsPackager (ASCII) pour l'env de dev.
 */
@Component
public class McJposClient {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(McJposClient.class);

    // Codes DE70 (Network Management Information Code)
    public static final String DE70_SIGNON  = "061"; // General sign-on by the processor
    public static final String DE70_SIGNOFF = "062"; // General sign-off by the processor
    public static final String DE70_ECHO    = "270"; // Echo test

    @Value("${mc.host:localhost}")
    private String host;

    @Value("${mc.port:7001}")
    private int port;

    @Value("${mc.forwarding-id:000000000001}")
    private String forwardingId;

    private final ISOPackager packager = new MastercardSmsPackager();
    private final AtomicInteger stan = new AtomicInteger(1);

    private Socket socket;
    private OutputStream out;
    private InputStream in;

    // ================================================================
    // CONNEXION
    // ================================================================

    public synchronized void connect() throws Exception {
        if (socket != null && !socket.isClosed()) return;
        log.info("[MC-CLI] Connexion au MIP {}:{}", host, port);
        socket = new Socket(host, port);
        socket.setSoTimeout(30_000);
        out = new BufferedOutputStream(socket.getOutputStream());
        in  = new BufferedInputStream(socket.getInputStream());
        log.info("[MC-CLI] Connecte au MIP {}:{}", host, port);
    }

    public synchronized boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    @PreDestroy
    public synchronized void disconnect() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
    }

    // ================================================================
    // SEND / RECEIVE
    // ================================================================

    /**
     * Envoie un ISOMsg et attend la reponse (framing 2 octets big-endian).
     * A adapter selon le framing reel du MIP Mastercard.
     */
    public ISOMsg sendAndWait(ISOMsg req, int timeoutSec) throws Exception {
        byte[] packed = packager.pack(req);
        log.info("[MC-CLI] >>> EMIS MTI={} DE70={} len={}",
                req.getMTI(),
                req.hasField(70) ? req.getString(70) : "-",
                packed.length);

        // Framing : 2 octets big-endian de longueur
        byte[] frame = new byte[2 + packed.length];
        frame[0] = (byte)((packed.length >> 8) & 0xFF);
        frame[1] = (byte)(packed.length & 0xFF);
        System.arraycopy(packed, 0, frame, 2, packed.length);
        out.write(frame);
        out.flush();

        // Lire la reponse
        socket.setSoTimeout(timeoutSec * 1000);
        byte[] lenBuf = new byte[2];
        if (in.read(lenBuf, 0, 2) < 2)
            throw new IOException("Framing incomplet");
        int respLen = ((lenBuf[0] & 0xFF) << 8) | (lenBuf[1] & 0xFF);
        byte[] respBytes = new byte[respLen];
        int read = 0;
        while (read < respLen) {
            int n = in.read(respBytes, read, respLen - read);
            if (n < 0) throw new IOException("Connexion fermee");
            read += n;
        }

        ISOMsg resp = new ISOMsg();
        resp.setPackager(packager);
        packager.unpack(resp, respBytes);
        log.info("[MC-CLI] <<< RECU MTI={} DE39={} DE70={}",
                resp.getMTI(),
                resp.hasField(39) ? resp.getString(39) : "-",
                resp.hasField(70) ? resp.getString(70) : "-");
        return resp;
    }

    // ================================================================
    // CONSTRUCTION DES MESSAGES
    // ================================================================

    private String nextStan() {
        return String.format("%06d", stan.getAndIncrement() % 1_000_000);
    }

    /** Date/heure UTC au format MMDDhhmmss (DE7). */
    private String utcDateTime() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
    }

    /**
     * Construit un message 0800 (Network Management Request).
     * @param de70 le code DE70 : DE70_SIGNON, DE70_SIGNOFF, DE70_ECHO
     */
    public ISOMsg buildNetworkRequest(String de70) throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        m.setMTI("0800");
        m.set(7,  utcDateTime());           // Transmission Date and Time (UTC)
        m.set(11, nextStan());              // STAN
        m.set(33, forwardingId);            // Forwarding Institution ID
        m.set(70, de70);                    // Network Management Information Code
        return m;
    }

    // ================================================================
    // FLUX RESEAU
    // ================================================================

    /** Sign-on : 0800 DE70=061 -> 0810. */
    public ISOMsg signon() throws Exception {
        connect();
        ISOMsg req = buildNetworkRequest(DE70_SIGNON);
        log.info("[MC-CLI] Sign-on (0800 DE70={})", DE70_SIGNON);
        return sendAndWait(req, 30);
    }

    /** Echo test : 0800 DE70=270 -> 0810. */
    public ISOMsg echo() throws Exception {
        connect();
        ISOMsg req = buildNetworkRequest(DE70_ECHO);
        log.info("[MC-CLI] Echo test (0800 DE70={})", DE70_ECHO);
        return sendAndWait(req, 30);
    }

    /** Sign-off : 0800 DE70=062 -> 0810. */
    public ISOMsg signoff() throws Exception {
        connect();
        ISOMsg req = buildNetworkRequest(DE70_SIGNOFF);
        log.info("[MC-CLI] Sign-off (0800 DE70={})", DE70_SIGNOFF);
        ISOMsg resp = sendAndWait(req, 30);
        disconnect();
        return resp;
    }
}
