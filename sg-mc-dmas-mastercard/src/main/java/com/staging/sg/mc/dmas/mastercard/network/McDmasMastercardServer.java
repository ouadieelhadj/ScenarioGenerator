package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.McDmasLengthChannel;
import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Transport reseau cote MASTERCARD DMAS simule.
 *
 * LIAISON PERMANENTE : l'ISOServer accepte la connexion du membre et
 * conserve la session (ISOSource) pour pouvoir y ECRIRE a tout moment.
 * Meme schema que SwamJposServer et McSmsJposServer.
 *
 * ------------------------------------------------------------------
 *  SEPARATION DES RESPONSABILITES
 * ------------------------------------------------------------------
 * Cette classe ne fait que du TRANSPORT : accepter, lire, router,
 * envoyer. Toute la DECISION metier est dans McDmasMastercardHandler
 * (moteur d'autorisation, reversal, advices, import de cles).
 *
 *     0800  ->  handler.buildNetworkResponse         ->  0810
 *     0100  ->  handler.buildAuthResponse            ->  0110
 *     0400  ->  handler.buildReversalResponse        ->  0410
 *     0120  ->  handler.buildAdviceResponse          ->  0130
 *     0420  ->  handler.buildReversalAdviceResponse  ->  0430
 *
 * REMPLACE McDmasMastercardClient : avant l'inversion, ce module etait
 * client et se connectait au membre — l'inverse de la realite.
 */
@Component
public class McDmasMastercardServer {

    private static final Logger log = LoggerFactory.getLogger(McDmasMastercardServer.class);

    private static final String NETWORK_CODE     = "DMAS";
    private static final int    DEFAULT_ISO_PORT = 8500;

    private final NetworkRepository networkRepository;
    private final McDmasMastercardHandler handler;

    @Value("${dmas.member-group:TESTGRP01}")
    private String memberGroup;

    private ISOServer isoServer;
    private Thread    serverThread;

    /** Session du membre, conservee pour pouvoir lui pousser des messages. */
    private volatile ISOSource activeMemberSession;
    private volatile String    activeMemberGroupId;
    private final Object sendLock = new Object();

    /** Correlation des reponses a NOS propres push. */
    private final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

    public McDmasMastercardServer(NetworkRepository networkRepository,
                                  @Lazy McDmasMastercardHandler handler) {
        this.networkRepository = networkRepository;
        this.handler = handler;
    }

    // ====================================================================
    //  CYCLE DE VIE
    // ====================================================================

    private int resolvePort() {
        try {
            Optional<NetworkRef> n = networkRepository.findByCode(NETWORK_CODE);
            if (n.isPresent() && n.get().getIssuerIsoPort() != null) {
                int p = n.get().getIssuerIsoPort();
                log.info("[JPOS-SRV] Port ISO lu depuis networks : {}", p);
                return p;
            }
            log.warn("[JPOS-SRV] Port ISO absent en base, fallback {}", DEFAULT_ISO_PORT);
        } catch (Exception e) {
            log.warn("[JPOS-SRV] Lecture du port KO ({}), fallback {}",
                    e.getMessage(), DEFAULT_ISO_PORT);
        }
        return DEFAULT_ISO_PORT;
    }

    @PostConstruct
    public void start() {
        int port = resolvePort();
        try {
            McPackagerEbcdic packager = new McPackagerEbcdic();
            McDmasLengthChannel channel = new McDmasLengthChannel();
            channel.setPackager(packager);

            isoServer = new ISOServer(port, channel, null);
            isoServer.addISORequestListener(new MemberListener());

            serverThread = new Thread(isoServer, "mc-dmas-mastercard-server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("[JPOS-SRV] ISOServer demarre sur :{} (McDmasLengthChannel/EBCDIC)", port);
        } catch (Exception e) {
            log.error("[JPOS-SRV] Echec du demarrage : {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (isoServer != null) isoServer.shutdown();
        if (serverThread != null) serverThread.interrupt();
        log.info("[JPOS-SRV] ISOServer arrete");
    }

    // ====================================================================
    //  API PUBLIQUE
    // ====================================================================

    public boolean hasActiveSession() {
        return activeMemberSession != null;
    }

    public String getActiveMemberGroupId() {
        return activeMemberGroupId;
    }

    /** Pousse un message vers le membre et attend sa reponse (correlee par STAN). */
    public ISOMsg pushAndWait(ISOMsg msg, int timeoutSeconds) throws Exception {
        if (activeMemberSession == null) {
            throw new IllegalStateException(
                    "Pas de session membre active — le membre doit faire un sign-on");
        }
        String stan = msg.hasField(11) ? msg.getString(11) : null;
        if (stan == null) {
            throw new IllegalStateException("DE11 (STAN) requis pour correler la reponse");
        }

        CompletableFuture<ISOMsg> fut = new CompletableFuture<>();
        pending.put(stan, fut);
        try {
            synchronized (sendLock) {
                activeMemberSession.send(msg);
            }
            return fut.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.io.IOException e) {
            activeMemberSession = null;
            activeMemberGroupId = null;
            log.error("[JPOS-SRV] Session membre perimee : {}", e.getMessage());
            throw new IllegalStateException(
                    "Session membre perimee — le membre doit se reconnecter", e);
        } finally {
            pending.remove(stan);
        }
    }

    /** Pousse un message SANS attendre de reponse. */
    public void pushOnActiveSession(ISOMsg msg) throws Exception {
        if (activeMemberSession == null) {
            throw new IllegalStateException("Pas de session membre active");
        }
        synchronized (sendLock) {
            activeMemberSession.send(msg);
        }
    }

    // ====================================================================
    //  LISTENER — transport uniquement
    // ====================================================================

    private class MemberListener implements ISORequestListener {

        @Override
        public boolean process(ISOSource source, ISOMsg m) {
            try {
                String mti  = m.getMTI();
                String stan = m.hasField(11) ? m.getString(11) : null;

                // Reponse a un de NOS push ?
                CompletableFuture<ISOMsg> fut = (stan != null) ? pending.remove(stan) : null;
                if (fut != null) {
                    log.info("[JPOS-SRV] Reponse correlee : MTI={} STAN={}", mti, stan);
                    fut.complete(m);
                    return true;
                }

                // Toute requete entrante reactive la session
                if (activeMemberSession == null) {
                    activeMemberSession = source;
                }

                ISOMsg resp = switch (mti) {
                    case "0800" -> {
                        trackSession(source, m);
                        yield handler.buildNetworkResponse(m);
                    }
                    case "0100", "0200" -> handler.buildAuthResponse(m);
                    case "0400"         -> handler.buildReversalResponse(m);
                    case "0120"         -> handler.buildAdviceResponse(m);
                    case "0420"         -> handler.buildReversalAdviceResponse(m);
                    default             -> null;
                };

                if (resp == null) {
                    log.warn("[JPOS-SRV] MTI non gere : {}", mti);
                    return false;
                }

                source.send(resp);
                log.info("[JPOS-SRV] Repondu {} DE39={} STAN={}",
                        resp.getMTI(),
                        resp.hasField(39) ? resp.getString(39) : "?",
                        stan);
                return true;

            } catch (Exception e) {
                log.error("[JPOS-SRV] Erreur de traitement : {}", e.getMessage(), e);
                return false;
            }
        }

        /** Suit l'etat de la session selon le code de gestion reseau. */
        private void trackSession(ISOSource source, ISOMsg m) {
            String de70 = m.hasField(70) ? m.getString(70) : "";
            // 001 et 061 coexistent : ancien mecanisme et nouveau client.
            // A unifier une fois le code DMAS confirme par les specifications.
            if ("061".equals(de70) || "001".equals(de70)) {
                activeMemberSession = source;
                activeMemberGroupId = m.hasField(2) ? m.getString(2) : memberGroup;
                log.info("[JPOS-SRV] Session membre enregistree (id={})", activeMemberGroupId);
            } else if ("062".equals(de70) || "002".equals(de70)) {
                log.info("[JPOS-SRV] Sign-off — session liberee");
                activeMemberSession = null;
                activeMemberGroupId = null;
            }
        }
    }
}
