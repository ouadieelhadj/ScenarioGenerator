package com.staging.sg.mc.sms.acquirer.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.MastercardSmsPackager;
import com.staging.sg.common.iso.McSmsLengthChannel;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client jPOS cote MEMBRE (Mastercard SMS acquirer).
 * Liaison PERMANENTE bidirectionnelle : le MIP peut aussi ecrire.
 *
 * Modele : SwamJposClient (meme pattern receiver + correlation par STAN).
 *
 * Flux reseau Mastercard SMS :
 *   1. Membre -> MIP : 0800 DE70=061 (Sign-on)
 *   2. MIP -> Membre : 0810 DE39=00  (Reponse sign-on)
 *   3. Membre -> MIP : 0800 DE70=270 (Echo test, keep-alive periodique)
 *   4. MIP -> Membre : 0810 DE39=00
 *   5. Membre -> MIP : 0800 DE70=062 (Sign-off)
 *
 * Le MIP peut pousser spontanement :
 *   - 0800 DE70=161 (PEK exchange) -> le membre importe la cle et repond 0810
 *   - 0800 DE70=301 (cutover)      -> le membre accuse
 *
 * NOTE : il n'y a PAS de MAC dans le SMS (DE64 et DE128 non utilises).
 *        L'authentification du sign-on passe par le DE96 (Message Security Code).
 */
@Component
public class McSmsJposClient {

    private static final Logger log = LoggerFactory.getLogger(McSmsJposClient.class);

    private static final String NETWORK_CODE = "MASTERCARD_SMS";
    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8098;

    /** Codes DE70 (Network Management Information Code). */
    public static final String DE70_SIGNON       = "061";
    public static final String DE70_SIGNOFF      = "062";
    public static final String DE70_PEK_EXCHANGE = "161";
    public static final String DE70_ECHO         = "270";
    public static final String DE70_CUTOVER      = "301";

    private final NetworkRepository networkRepository;
    private final MastercardSmsPackager packager = new MastercardSmsPackager();
    private McSmsLengthChannel channel;

    @Value("${mc.sms.forwarding-id:0000000001}")
    private String forwardingId;

    private final ConcurrentHashMap<String, ISOMsg> responses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final AtomicInteger stanSeq = new AtomicInteger(1);

    private Thread receiver;
    private volatile boolean running = false;

    public McSmsJposClient(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    // ========================================================================
    //  RESOLUTION HOST / PORT
    // ========================================================================

    private String host() {
        try {
            Optional<NetworkRef> n = networkRepository.findByCode(NETWORK_CODE);
            if (n.isPresent() && n.get().getIssuerHost() != null) return n.get().getIssuerHost();
        } catch (Exception e) {
            log.warn("[MC-CLI] host base KO: {}", e.getMessage());
        }
        return DEFAULT_HOST;
    }

    private int port() {
        try {
            Optional<NetworkRef> n = networkRepository.findByCode(NETWORK_CODE);
            if (n.isPresent() && n.get().getIssuerIsoPort() != null) return n.get().getIssuerIsoPort();
        } catch (Exception e) {
            log.warn("[MC-CLI] port base KO: {}", e.getMessage());
        }
        return DEFAULT_PORT;
    }

    // ========================================================================
    //  CONNEXION PERMANENTE
    // ========================================================================

    public synchronized void connect() throws Exception {
        if (channel != null && channel.isConnected()) return;
        String h = host();
        int p = port();
        channel = new McSmsLengthChannel(packager);
        channel.setHost(h);
        channel.setPort(p);
        channel.connect();
        running = true;
        startReceiver();
        log.info("[MC-CLI] Connecte au MIP {}:{}", h, p);
    }

    /**
     * Receiver : traite les messages entrants en continu.
     *   - 0810 (reponse)  -> correlation par STAN via latch
     *   - 0800 (spontane) -> traite immediatement (PEK exchange, cutover)
     */
    private void startReceiver() {
        receiver = new Thread(() -> {
            while (running && channel != null && channel.isConnected()) {
                try {
                    ISOMsg msg  = channel.receive();
                    String mti  = msg.getMTI();
                    String stan = msg.hasField(11) ? msg.getString(11) : null;

                    // ── Message SPONTANE du MIP (0800) ──────────────────────
                    if ("0800".equals(mti)) {
                        String de70 = msg.hasField(70) ? msg.getString(70) : "?";
                        log.info("[MC-CLI] Message SPONTANE du MIP : 0800 DE70={} STAN={}", de70, stan);
                        handleMipPush(msg, de70);
                        continue;
                    }

                    // ── Reponse correlee par STAN (0810, 0210, ...) ─────────
                    if (stan != null) {
                        responses.put(stan, msg);
                        CountDownLatch l = latches.get(stan);
                        if (l != null) l.countDown();
                    }
                } catch (Exception e) {
                    if (running) log.warn("[MC-CLI] receiver: {}", e.getMessage());
                    break;
                }
            }
            log.info("[MC-CLI] Receiver arrete");
        }, "mc-sms-client-receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    /**
     * Traite un message pousse spontanement par le MIP.
     * Pour l'instant : accuse reception. Le PEK exchange (DE70=161) sera
     * implemente quand on aura la structure du DE48 subelement 11.
     */
    private void handleMipPush(ISOMsg msg, String de70) {
        try {
            switch (de70) {
                case DE70_PEK_EXCHANGE:
                    log.info("[MC-CLI] PEK exchange recu (DE70=161) — import a implementer");
                    // TODO : parser DE48 subelement 11, importer la PEK sous LMK,
                    //        persister dans mc_sms_acq_keys
                    sendAck(msg, "00");
                    break;

                case DE70_CUTOVER:
                    log.info("[MC-CLI] Cutover recu (DE70=301)");
                    sendAck(msg, "00");
                    break;

                case DE70_ECHO:
                    log.info("[MC-CLI] Echo test recu du MIP (DE70=270)");
                    sendAck(msg, "00");
                    break;

                default:
                    log.warn("[MC-CLI] 0800 DE70={} non gere — accuse quand meme", de70);
                    sendAck(msg, "00");
            }
        } catch (Exception e) {
            log.error("[MC-CLI] handleMipPush erreur : {}", e.getMessage(), e);
            try { sendAck(msg, "96"); } catch (Exception ignore) {}
        }
    }

    /**
     * Envoie un 0810 en reponse a un 0800 pousse par le MIP.
     * DE7, DE11, DE33 et DE70 doivent reprendre les valeurs de la requete (ME).
     */
    private void sendAck(ISOMsg req, String de39) throws Exception {
        ISOMsg ack = new ISOMsg();
        ack.setPackager(packager);
        ack.setMTI("0810");
        if (req.hasField(7))  ack.set(7,  req.getString(7));
        else                  ack.set(7,  utcDateTime());
        if (req.hasField(11)) ack.set(11, req.getString(11));
        if (req.hasField(33)) ack.set(33, req.getString(33));
        ack.set(39, de39);
        if (req.hasField(63)) ack.set(63, req.getString(63));
        if (req.hasField(70)) ack.set(70, req.getString(70));
        channel.send(ack);
        log.info("[MC-CLI] Accuse envoye : 0810 DE39={}", de39);
    }

    // ========================================================================
    //  ENVOI AVEC ATTENTE DE REPONSE
    // ========================================================================

    /** Envoie un message et attend la reponse correlee par STAN. */
    public ISOMsg sendAndWait(ISOMsg req, int timeoutSeconds) throws Exception {
        connect();
        String stan = req.getString(11);
        CountDownLatch latch = new CountDownLatch(1);
        latches.put(stan, latch);
        channel.send(req);
        boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
        latches.remove(stan);
        ISOMsg resp = responses.remove(stan);
        if (!ok || resp == null) {
            throw new RuntimeException("Timeout Mastercard SMS (STAN=" + stan + ")");
        }
        return resp;
    }

    // ========================================================================
    //  CONSTRUCTION DES MESSAGES
    // ========================================================================

    private String nextStan() {
        return String.format("%06d", stanSeq.getAndIncrement() % 1_000_000);
    }

    /** DE7 : Transmission Date and Time, en UTC, format MMDDhhmmss (guide p.345). */
    private String utcDateTime() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
    }

    /**
     * Construit un 0800 (Network Management Request).
     * Layout Table 74 : DE7 (M), DE11 (M), DE33 (M), DE70 (M), DE96 (C).
     */
    public ISOMsg buildNetworkRequest(String de70) throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        m.setMTI("0800");
        m.set(7,  utcDateTime());
        m.set(11, nextStan());
        m.set(33, forwardingId);
        m.set(70, de70);
        return m;
    }

    // ========================================================================
    //  FLUX RESEAU
    // ========================================================================

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

    /** Sign-off : 0800 DE70=062 -> 0810. La liaison reste ouverte. */
    public ISOMsg signoff() throws Exception {
        connect();
        ISOMsg req = buildNetworkRequest(DE70_SIGNOFF);
        log.info("[MC-CLI] Sign-off (0800 DE70={})", DE70_SIGNOFF);
        return sendAndWait(req, 30);
    }

    public boolean isConnected() { return channel != null && channel.isConnected(); }
    public MastercardSmsPackager getPackager() { return packager; }

    @PreDestroy
    public void disconnect() {
        running = false;
        try { if (channel != null) channel.disconnect(); } catch (Exception ignore) {}
        log.info("[MC-CLI] Deconnecte");
    }
}
