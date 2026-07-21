package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.entity.McDmasInterface;
import com.staging.sg.common.iso.McDmasLengthChannel;
import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.service.McDmasInterfaceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Transport reseau cote MASTERCARD DMAS.
 *
 * ------------------------------------------------------------------
 *  UN ISOServer PAR INTERFACE, DANS LA MEME JVM
 * ------------------------------------------------------------------
 *     --sg.interface=DMAS_MASTERCARD_1                    un serveur
 *     --sg.interface=DMAS_MASTERCARD_1,DMAS_MASTERCARD_2  deux serveurs
 *
 * Chaque interface ouvre son propre ISOServer sur son port ISO et
 * conserve les sessions des membres qui s'y connectent :
 *
 *     002202 -> ISOServer:8500 -> sessions des membres connectes
 *     002203 -> ISOServer:8503 -> sessions des membres connectes
 *
 * ------------------------------------------------------------------
 *  IDENTIFICATION DU MEMBRE
 * ------------------------------------------------------------------
 * Un membre est reconnu au sign-on par le DE2 (Group Sign-on ID). Le
 * Mastercard remonte ensuite a sa banque et a son member_group_id :
 *
 *     DE2=40260  ->  banque 022905  ->  member_group_id TESTGRP01
 *
 * Les sessions sont donc indexees par DE2, ce qui permet a plusieurs
 * membres de partager le meme serveur.
 *
 * Cette classe ne fait que du TRANSPORT ; la decision metier est dans
 * McDmasMastercardHandler.
 */
@Component
public class McDmasMastercardServer {

    private static final Logger log = LoggerFactory.getLogger(McDmasMastercardServer.class);

    private static final int DEFAULT_ISO_PORT = 8500;

    private final McDmasInterfaceService iface;
    private final McDmasMastercardHandler handler;
    private final McDmasKeyDelivery keyDelivery;

    /** Un serveur par interface Mastercard pilotee. Cle : bank_code. */
    private final Map<String, ServerInstance> servers = new ConcurrentHashMap<>();

    public McDmasMastercardServer(McDmasInterfaceService iface,
                                  @Lazy McDmasMastercardHandler handler,
                                  @Lazy McDmasKeyDelivery keyDelivery) {
        this.iface = iface;
        this.handler = handler;
        this.keyDelivery = keyDelivery;
    }

    // ==================================================================
    //  UN SERVEUR = UN PORT ISO + SES SESSIONS
    // ==================================================================

    private class ServerInstance {
        final String bankCode;
        final McDmasInterface cfg;
        final int port;

        ISOServer isoServer;
        Thread    thread;

        /** Sessions des membres connectes, indexees par DE2. */
        final Map<String, ISOSource> sessions = new ConcurrentHashMap<>();

        /** Derniere session vue, pour les cas ou le DE2 manque. */
        volatile ISOSource lastSession;
        volatile String    lastDe2;

        final Object sendLock = new Object();
        final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

        ServerInstance(McDmasInterface cfg) {
            this.cfg = cfg;
            this.bankCode = cfg.getBankCode();
            this.port = cfg.getIsoPort() != null ? cfg.getIsoPort() : DEFAULT_ISO_PORT;
        }

        void start() {
            try {
                McPackagerEbcdic packager = new McPackagerEbcdic();
                McDmasLengthChannel channel = new McDmasLengthChannel();
                channel.setPackager(packager);

                isoServer = new ISOServer(port, channel, null);
                isoServer.addISORequestListener(new MemberListener(this));

                thread = new Thread(isoServer, "mc-dmas-server-" + bankCode);
                thread.setDaemon(true);
                thread.start();
                log.info("[JPOS-SRV:{}] ISOServer demarre sur :{} (McDmasLengthChannel/EBCDIC)",
                        bankCode, port);
            } catch (Exception e) {
                log.error("[JPOS-SRV:{}] Echec du demarrage sur :{} — {}",
                        bankCode, port, e.getMessage(), e);
            }
        }

        void stop() {
            if (isoServer != null) isoServer.shutdown();
            if (thread != null) thread.interrupt();
            sessions.clear();
            log.info("[JPOS-SRV:{}] ISOServer arrete", bankCode);
        }

        /** Session a utiliser : celle du DE2 demande, ou la derniere vue. */
        ISOSource session(String de2) {
            if (de2 != null) {
                ISOSource s = sessions.get(de2);
                if (s != null) return s;
            }
            return lastSession;
        }
    }

    // ==================================================================
    //  CYCLE DE VIE
    // ==================================================================

    @PostConstruct
    public void start() {
        for (McDmasInterface i : iface.all()) {
            if (i.getIsoPort() == null) {
                log.warn("[JPOS-SRV:{}] iso_port absent — pas de serveur pour cette interface",
                        i.getBankCode());
                continue;
            }
            ServerInstance s = new ServerInstance(i);
            servers.put(i.getBankCode(), s);
            s.start();
            iface.setStatus(i.getBankCode(), McDmasInterfaceService.READY);
        }
        if (servers.size() > 1) {
            log.info("[JPOS-SRV] {} serveurs ISO demarres", servers.size());
        }
    }

    @PreDestroy
    public void stop() {
        servers.forEach((bank, s) -> {
            s.stop();
            iface.setStatus(bank, McDmasInterfaceService.OFF);
        });
    }

    // ==================================================================
    //  API
    // ==================================================================

    private ServerInstance server(String bankCode) {
        if (bankCode == null || bankCode.isBlank()) {
            return servers.values().stream().findFirst().orElseThrow(
                    () -> new IllegalStateException("Aucun serveur ISO demarre"));
        }
        ServerInstance s = servers.get(bankCode);
        if (s == null) {
            throw new IllegalArgumentException(
                    "Aucun serveur pour la banque " + bankCode + ". Disponibles : "
                  + String.join(", ", servers.keySet()));
        }
        return s;
    }

    public boolean hasActiveSession() { return hasActiveSession(null); }

    public boolean hasActiveSession(String bankCode) {
        try {
            return !server(bankCode).sessions.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public String getActiveMemberGroupId() { return getActiveMemberGroupId(null); }

    /** DE2 du dernier membre vu sur ce serveur. */
    public String getActiveMemberGroupId(String bankCode) {
        try {
            return server(bankCode).lastDe2;
        } catch (Exception e) {
            return null;
        }
    }

    /** Membres connectes, par serveur. */
    public Map<String, Object> sessions() {
        Map<String, Object> r = new LinkedHashMap<>();
        servers.forEach((bank, s) -> r.put(bank, s.sessions.keySet()));
        return r;
    }

    public ISOMsg pushAndWait(ISOMsg msg, int timeoutSeconds) throws Exception {
        return pushAndWait(null, msg, timeoutSeconds);
    }

    /** Pousse vers le membre identifie par le DE2 du message. */
    public ISOMsg pushAndWait(String bankCode, ISOMsg msg, int timeoutSeconds) throws Exception {
        ServerInstance s = server(bankCode);
        String de2 = msg.hasField(2) ? msg.getString(2) : null;
        ISOSource src = s.session(de2);
        if (src == null) {
            throw new IllegalStateException(
                    "Pas de session membre active sur " + s.bankCode
                  + " — le membre doit faire un sign-on");
        }

        String stan = msg.hasField(11) ? msg.getString(11) : null;
        if (stan == null) {
            throw new IllegalStateException("DE11 (STAN) requis pour correler la reponse");
        }

        CompletableFuture<ISOMsg> fut = new CompletableFuture<>();
        s.pending.put(stan, fut);
        try {
            synchronized (s.sendLock) {
                src.send(msg);
            }
            return fut.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.io.IOException e) {
            if (de2 != null) s.sessions.remove(de2);
            log.error("[JPOS-SRV:{}] Session membre perimee : {}", s.bankCode, e.getMessage());
            throw new IllegalStateException("Session membre perimee", e);
        } finally {
            s.pending.remove(stan);
        }
    }

    public void pushOnActiveSession(ISOMsg msg) throws Exception {
        pushOnActiveSession(null, msg);
    }

    public void pushOnActiveSession(String bankCode, ISOMsg msg) throws Exception {
        ServerInstance s = server(bankCode);
        String de2 = msg.hasField(2) ? msg.getString(2) : null;
        ISOSource src = s.session(de2);
        if (src == null) {
            throw new IllegalStateException("Pas de session membre active sur " + s.bankCode);
        }
        synchronized (s.sendLock) {
            src.send(msg);
        }
    }

    // ==================================================================
    //  LISTENER
    // ==================================================================

    private class MemberListener implements ISORequestListener {

        private final ServerInstance srv;

        MemberListener(ServerInstance srv) { this.srv = srv; }

        @Override
        public boolean process(ISOSource source, ISOMsg m) {
            try {
                String mti  = m.getMTI();
                String stan = m.hasField(11) ? m.getString(11) : null;
                String de2  = m.hasField(2)  ? m.getString(2)  : null;

                // Reponse a un de NOS push ?
                CompletableFuture<ISOMsg> fut = (stan != null) ? srv.pending.remove(stan) : null;
                if (fut != null) {
                    log.info("[JPOS-SRV:{}] Reponse correlee : MTI={} STAN={}",
                            srv.bankCode, mti, stan);
                    fut.complete(m);
                    return true;
                }

                srv.lastSession = source;
                if (de2 != null) srv.lastDe2 = de2;

                ISOMsg resp = switch (mti) {
                    case "0800" -> {
                        trackSession(srv, source, m);
                        yield handler.buildNetworkResponse(m);
                    }
                    case "0100", "0200" -> handler.buildAuthResponse(m);
                    case "0400"         -> handler.buildReversalResponse(m);
                    case "0120"         -> handler.buildAdviceResponse(m);
                    case "0420"         -> handler.buildReversalAdviceResponse(m);
                    default             -> null;
                };

                if (resp == null) {
                    log.warn("[JPOS-SRV:{}] MTI non gere : {}", srv.bankCode, mti);
                    return false;
                }

                source.send(resp);
                String rc = resp.hasField(39) ? resp.getString(39) : "?";
                log.info("[JPOS-SRV:{}] Repondu {} DE39={} STAN={}",
                        srv.bankCode, resp.getMTI(), rc, stan);

                // Sollicitation acceptee : livrer la cle de facon asynchrone
                String de70 = m.hasField(70) ? m.getString(70) : "";
                if ("0800".equals(mti) && "162".equals(de70) && "00".equals(rc)) {
                    log.info("[JPOS-SRV:{}] Sollicitation 162 acceptee — livraison de la cle",
                            srv.bankCode);
                    keyDelivery.deliverAsync(srv.bankCode, m);
                }
                return true;

            } catch (Exception e) {
                log.error("[JPOS-SRV:{}] Erreur de traitement : {}",
                        srv.bankCode, e.getMessage(), e);
                return false;
            }
        }

        /** Suit l'etat de la session selon le code de gestion reseau. */
        private void trackSession(ServerInstance srv, ISOSource source, ISOMsg m) {
            String de70 = m.hasField(70) ? m.getString(70) : "";
            String de2  = m.hasField(2)  ? m.getString(2)  : null;

            // 001 et 061 coexistent : sign-on par BIN ou par Group Sign-on ID.
            if ("061".equals(de70) || "001".equals(de70)) {
                if (de2 != null) {
                    srv.sessions.put(de2, source);
                    McDmasInterface membre = iface.lookupByGroupSignon(de2);
                    log.info("[JPOS-SRV:{}] Membre connecte : DE2={} -> banque {} ({})",
                            srv.bankCode, de2,
                            membre != null ? membre.getBankCode() : "inconnue",
                            membre != null ? membre.getMemberGroupId() : "?");
                    if (membre != null) {
                        iface.markStatus(membre.getIdInterface(), McDmasInterfaceService.SIGNON);
                    }
                } else {
                    log.warn("[JPOS-SRV:{}] Sign-on sans DE2 — session non indexee", srv.bankCode);
                }
            } else if ("062".equals(de70) || "002".equals(de70)) {
                if (de2 != null) srv.sessions.remove(de2);
                log.info("[JPOS-SRV:{}] Sign-off de DE2={} — session liberee",
                        srv.bankCode, de2);
                McDmasInterface membre = iface.lookupByGroupSignon(de2);
                if (membre != null) {
                    iface.markStatus(membre.getIdInterface(), McDmasInterfaceService.SIGNOFF);
                }
            }
        }
    }
}
