package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.McDmasLengthChannel;
import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client jPOS cote MEMBRE (notre banque) vers le reseau Mastercard DMAS.
 *
 * LIAISON PERMANENTE : le membre ouvre la socket, fait le sign-on, et la
 * garde ouverte. Un thread d'ecoute lit en continu tout ce qui arrive —
 * reponses correlees par STAN, et messages pousses par le reseau.
 *
 * Meme schema que SwamJposClient et McSmsJposClient.
 *
 * ------------------------------------------------------------------
 *  REMPLACE McDmasMemberServer
 * ------------------------------------------------------------------
 * Avant, le membre hebergeait un ISOServer et attendait que le module
 * mastercard vienne s'y connecter — l'inverse de la realite et des deux
 * autres reseaux.
 *
 * L'API PUBLIQUE EST CONSERVEE A L'IDENTIQUE pour que McDmasKeyExchange,
 * McDmasAuthorization et LoadTestService n'aient pas a changer :
 *
 *     start(), stop(), hasActiveSession(), getActiveMemberGroupId(),
 *     pushAndWait(msg, timeout), pushOnActiveSession(msg)
 *
 * "push" garde son sens : emettre vers l'autre extremite. Seule la
 * mecanique sous-jacente change (socket sortante au lieu d'entrante).
 */
@Component
public class McDmasMemberClient {

    private static final Logger log = LoggerFactory.getLogger(McDmasMemberClient.class);

    private static final String NETWORK_CODE = "DMAS";
    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 8500;

    private final NetworkRepository networkRepository;

    @Value("${dmas.member-group-id:TESTGRP01}")
    private String memberGroupId;

    @Value("${dmas.jpos.group-signon-id:40260}")
    private String groupSignonId;

    @Value("${dmas.jpos.forwarding-id:011901}")
    private String forwardingId;

    /** @Lazy : le service prend ce client au constructeur, cycle Spring sinon. */
    @Autowired @Lazy private McDmasKeyExchange keyExchange;

    private McPackagerEbcdic    packager;
    private McDmasLengthChannel channel;
    private Thread              listenerThread;
    private volatile boolean    running = false;
    private volatile boolean    signedOn = false;

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    /** Correlation : STAN -> future en attente de la reponse. */
    private final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

    public McDmasMemberClient(NetworkRepository networkRepository) {
        this.networkRepository = networkRepository;
    }

    // ====================================================================
    //  RESOLUTION HOST / PORT DEPUIS LA BASE
    // ====================================================================

    private String host() {
        try {
            Optional<NetworkRef> n = networkRepository.findByCode(NETWORK_CODE);
            if (n.isPresent() && n.get().getIssuerHost() != null) {
                return n.get().getIssuerHost();
            }
        } catch (Exception e) {
            log.warn("[JPOS-CLI] Lecture host en base KO : {}", e.getMessage());
        }
        return DEFAULT_HOST;
    }

    private int port() {
        try {
            Optional<NetworkRef> n = networkRepository.findByCode(NETWORK_CODE);
            if (n.isPresent() && n.get().getIssuerIsoPort() != null) {
                return n.get().getIssuerIsoPort();
            }
        } catch (Exception e) {
            log.warn("[JPOS-CLI] Lecture port en base KO : {}", e.getMessage());
        }
        return DEFAULT_PORT;
    }

    // ====================================================================
    //  CYCLE DE VIE
    // ====================================================================

    /**
     * Pas de @PostConstruct : la connexion est etablie a la demande
     * (premier signOn ou premier envoi), pour que le module demarre
     * meme si le reseau n'est pas encore la.
     */
    public void start() {
        try {
            ensureConnected();
        } catch (Exception e) {
            log.warn("[JPOS-CLI] Connexion differee : {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        signedOn = false;
        try {
            if (channel != null) channel.disconnect();
        } catch (Exception ignore) { }
        log.info("[JPOS-CLI] Deconnecte");
    }

    private synchronized void ensureConnected() throws Exception {
        if (channel != null && channel.isConnected()) return;

        String h = host();
        int    p = port();
        try {
            packager = new McPackagerEbcdic();
            channel  = new McDmasLengthChannel();
            channel.setPackager(packager);
            channel.setHost(h, p);
            log.info("[JPOS-CLI] Liaison permanente -> {}:{}", h, p);
            channel.connect();
            startListener();
        } catch (Exception e) {
            channel = null;
            log.error("[JPOS-CLI] Reseau injoignable sur {}:{} — {}", h, p, e.getMessage());
            throw new IllegalStateException(
                    "Reseau Mastercard DMAS indisponible sur " + h + ":" + p
                  + " — demarrer le module mastercard puis reessayer", e);
        }
    }

    /** Thread d'ecoute continu sur la liaison permanente. */
    private void startListener() {
        running = true;
        listenerThread = new Thread(() -> {
            while (running) {
                try {
                    ISOMsg m = channel.receive();
                    handleIncoming(m);
                } catch (Exception e) {
                    if (running) log.error("[JPOS-CLI] Erreur d'ecoute : {}", e.getMessage());
                    running = false;
                    signedOn = false;
                }
            }
            log.info("[JPOS-CLI] Listener arrete");
        }, "mc-dmas-member-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Route un message entrant : reponse correlee par STAN, ou message
     * pousse par le reseau (auquel cas on accuse reception).
     */
    private void handleIncoming(ISOMsg m) {
        try {
            String stan = m.hasField(11) ? m.getString(11) : null;
            CompletableFuture<ISOMsg> fut = (stan != null) ? pending.remove(stan) : null;
            if (fut != null) {
                fut.complete(m);
                return;
            }

            String mti  = m.getMTI();
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            log.info("[JPOS-CLI] Message POUSSE par le reseau : MTI={} DE70={} STAN={}",
                    mti, de70, stan);

            if ("0800".equals(mti)) {
                // DE70=161 : le reseau nous livre une cle (mecanisme 162)
                String rc = "161".equals(de70)
                        ? keyExchange.handleKeyDelivery(m)
                        : "00";
                sendAck(m, rc);
            } else if ("0820".equals(mti)) {
                log.info("[JPOS-CLI] Advice 0820 DE70={} recu", de70);
                if ("161".equals(de70)) {
                    // Acquittement : la cle livree devient utilisable
                    keyExchange.handleKeyAcknowledgement(m);
                }
            } else {
                log.warn("[JPOS-CLI] MTI pousse non gere : {}", mti);
            }
        } catch (Exception e) {
            log.error("[JPOS-CLI] Erreur de routage : {}", e.getMessage(), e);
        }
    }

    /** Accuse un 0800 pousse par le reseau, champs ME recopies. */
    private void sendAck(ISOMsg req, String de39) throws Exception {
        ISOMsg r = new ISOMsg();
        r.setPackager(req.getPackager());
        r.setMTI("0810");
        if (req.hasField(2))  r.set(2,  req.getString(2));
        r.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
        if (req.hasField(11)) r.set(11, req.getString(11));
        if (req.hasField(33)) r.set(33, req.getString(33));
        r.set(39, de39);
        if (req.hasField(48)) r.set(48, req.getString(48));
        if (req.hasField(70)) r.set(70, req.getString(70));
        channel.send(r);
        log.info("[JPOS-CLI] Accuse 0810 DE39={} envoye", de39);
    }

    // ====================================================================
    //  API PUBLIQUE — conservee a l'identique
    // ====================================================================

    /** true si la liaison est etablie et le sign-on accepte. */
    public boolean hasActiveSession() {
        return channel != null && channel.isConnected() && signedOn;
    }

    public String getActiveMemberGroupId() {
        return signedOn ? memberGroupId : null;
    }

    /** Emet un message sur la liaison permanente et attend SA reponse (STAN). */
    public ISOMsg pushAndWait(ISOMsg msg, int timeoutSeconds) throws Exception {
        ensureConnected();

        String stan = msg.hasField(11) ? msg.getString(11) : null;
        if (stan == null) {
            throw new IllegalStateException("DE11 (STAN) requis pour correler la reponse");
        }

        CompletableFuture<ISOMsg> fut = new CompletableFuture<>();
        pending.put(stan, fut);
        try {
            channel.send(msg);
            return fut.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.io.IOException e) {
            signedOn = false;
            log.error("[JPOS-CLI] Liaison rompue : {}", e.getMessage());
            throw new IllegalStateException(
                    "Liaison rompue avec le reseau — refaire un sign-on", e);
        } finally {
            pending.remove(stan);
        }
    }

    /** Emet un message SANS attendre de reponse (ex : 0820 advice). */
    public void pushOnActiveSession(ISOMsg msg) throws Exception {
        ensureConnected();
        channel.send(msg);
    }

    // ====================================================================
    //  SIGN-ON / ECHO
    // ====================================================================

    public synchronized Map<String, Object> signOn() throws Exception {
        ensureConnected();
        Map<String, Object> r = sendNetworkMessage("061", "SIGN-ON");
        signedOn = Boolean.TRUE.equals(r.get("success"));
        if (signedOn) {
            log.info("[JPOS-CLI] Sign-on accepte (memberGroup={})", memberGroupId);
        }
        return r;
    }

    public Map<String, Object> echoTest() throws Exception {
        ensureConnected();
        return sendNetworkMessage("270", "ECHO-TEST");
    }

    public Map<String, Object> signOff() throws Exception {
        ensureConnected();
        Map<String, Object> r = sendNetworkMessage("062", "SIGN-OFF");
        signedOn = false;
        return r;
    }

    /** Keep-alive : echo periodique pour maintenir la liaison. */
    @Scheduled(fixedRateString = "${dmas.jpos.echo-interval-ms:60000}")
    public void scheduledEcho() {
        if (!running || !signedOn) return;
        try {
            Map<String, Object> r = echoTest();
            log.debug("[JPOS-CLI] Keep-alive : {}", r.get("success"));
        } catch (Exception e) {
            log.warn("[JPOS-CLI] Keep-alive echoue : {}", e.getMessage());
        }
    }

    /** Construit et envoie un 0800 de gestion reseau, attend le 0810. */
    private Map<String, Object> sendNetworkMessage(String de70, String label) throws Exception {
        String stan = String.format("%06d", stanSeq.getAndIncrement() % 1_000_000);
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        m.setMTI("0800");
        m.set(2,  groupSignonId);
        m.set(7,  dt);
        m.set(11, stan);
        m.set(33, forwardingId);
        m.set(70, de70);
        m.set(94, "0I0    ");
        m.set(96, "000000");

        log.info("[JPOS-CLI] {} -> 0800 DE70={} STAN={}", label, de70, stan);
        ISOMsg resp = pushAndWait(m, 15);

        String rc = resp.hasField(39) ? resp.getString(39) : "??";
        boolean ok = "00".equals(rc);
        log.info("[JPOS-CLI] {} <- 0810 DE39={} ok={}", label, rc, ok);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("label",        label);
        r.put("mti_sent",     "0800");
        r.put("de070",        de70);
        r.put("stan",         stan);
        r.put("mti_received", resp.getMTI());
        r.put("de039",        rc);
        r.put("success",      ok);
        return r;
    }

    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }
}
