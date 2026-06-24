package com.staging.sg.dmas.acquirer.network;

import com.staging.sg.common.iso.McPackagerEbcdic;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import com.staging.sg.common.iso.DmasLengthChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur jPOS cote ACQUEREUR (= reseau Mastercard).
 * Connexion PERMANENTE avec l'issuer : garde la session active (ISOSource)
 * pour pouvoir y ECRIRE a tout moment (push du key exchange system-generated)
 * et correle les reponses a nos propres push par STAN (CompletableFuture),
 * en plus de repondre aux requetes entrantes (sign-on/echo de l'issuer).
 */
@Component
public class DmasJposServer {

    private static final Logger log = LoggerFactory.getLogger(DmasJposServer.class);

    @Value("${dmas.jpos.server-port:8600}")
    private int serverPort;

    private ISOServer isoServer;
    private Thread    serverThread;

    private volatile ISOSource activeIssuerSession;
    private volatile String    activeMemberGroupId;

    /** Correlation : STAN de NOS push -> future en attente de la reponse (0810/0820). */
    private final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        try {
            McPackagerEbcdic packager = new McPackagerEbcdic();
            DmasLengthChannel channel = new DmasLengthChannel();
            channel.setPackager(packager);

            isoServer = new ISOServer(serverPort, channel, null);
            isoServer.addISORequestListener(new SignOnListener());

            serverThread = new Thread(isoServer, "dmas-jpos-server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("[JPOS-SRV] ISOServer demarre sur :{} (DmasLengthChannel/EBCDIC)", serverPort);
        } catch (Exception e) {
            log.error("[JPOS-SRV] Echec demarrage : {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (isoServer != null) isoServer.shutdown();
        if (serverThread != null) serverThread.interrupt();
        log.info("[JPOS-SRV] ISOServer arrete");
    }

    public boolean hasActiveSession() {
        return activeIssuerSession != null;
    }

    public String getActiveMemberGroupId() {
        return activeMemberGroupId;
    }

    /** Pousse un message sur la connexion permanente et attend SA reponse (correlee par STAN). */
    public ISOMsg pushAndWait(ISOMsg msg, int timeoutSeconds) throws Exception {
        if (activeIssuerSession == null)
            throw new IllegalStateException(
                "Pas de session issuer active - l'issuer doit faire un sign-on avant tout key exchange");
        String stan = msg.hasField(11) ? msg.getString(11) : null;
        if (stan == null) throw new IllegalStateException("DE11 (STAN) requis pour correler la reponse");

        CompletableFuture<ISOMsg> fut = new CompletableFuture<>();
        pending.put(stan, fut);
        try {
            activeIssuerSession.send(msg);
            return fut.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.io.IOException e) {
            // Session perimee (ex: issuer redemarre sans nouveau sign-on detecte)
            activeIssuerSession = null;
            activeMemberGroupId = null;
            log.error("[JPOS-SRV] Session issuer perimee/invalide : {} - l'issuer doit refaire un sign-on", e.getMessage());
            throw new IllegalStateException(
                "Session issuer perimee (l'issuer a probablement redemarre) - refaire un sign-on", e);
        } finally {
            pending.remove(stan);
        }
    }

    /** Pousse un message SANS attendre de reponse (ex: 0820 advice). */
    public void pushOnActiveSession(ISOMsg msg) throws Exception {
        if (activeIssuerSession == null)
            throw new IllegalStateException("Pas de session issuer active (sign-on non recu)");
        activeIssuerSession.send(msg);
    }

    private class SignOnListener implements ISORequestListener {
        @Override
        public boolean process(ISOSource source, ISOMsg m) {
            try {
                String mti  = m.getMTI();
                String stan = m.hasField(11) ? m.getString(11) : null;

                // 1. Est-ce la reponse a un de NOS push (ex: 0810 suite a notre 0800 PEK) ?
                CompletableFuture<ISOMsg> fut = (stan != null) ? pending.remove(stan) : null;
                if (fut != null) {
                    log.info("[JPOS-SRV] Reponse correlee recue : MTI={} STAN={}", mti, stan);
                    fut.complete(m);
                    return true; // deja traite, pas de reponse a renvoyer
                }

                // 2. Sinon, c'est une requete entrante normale (sign-on/echo de l'issuer)
                String de70 = m.hasField(70) ? m.getString(70) : "?";
                log.info("[JPOS-SRV] Recu MTI={} DE70={} STAN={}", mti, de70, stan);

                if (!"0800".equals(mti)) {
                    log.warn("[JPOS-SRV] MTI non gere par ce listener : {}", mti);
                    return false;
                }

                if ("061".equals(de70) && m.hasField(2)) {
                    activeIssuerSession = source;
                    activeMemberGroupId = m.getString(2);
                    log.info("[JPOS-SRV] Session issuer enregistree (memberGroupId={})", activeMemberGroupId);
                }

                ISOMsg r = new ISOMsg();
                r.setPackager(m.getPackager());
                r.setMTI("0810");
                if (m.hasField(2))  r.set(2,  m.getString(2));
                r.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
                if (m.hasField(11)) r.set(11, m.getString(11));
                if (m.hasField(33)) r.set(33, m.getString(33));
                r.set(39, "00");
                r.set(63, "MCC000NPQ");
                if (m.hasField(70)) r.set(70, m.getString(70));

                source.send(r);
                log.info("[JPOS-SRV] Repondu 0810 DE39=00 (echo DE70={})", de70);
                return true;
            } catch (Exception e) {
                log.error("[JPOS-SRV] Erreur traitement : {}", e.getMessage(), e);
                return false;
            }
        }
    }
}
