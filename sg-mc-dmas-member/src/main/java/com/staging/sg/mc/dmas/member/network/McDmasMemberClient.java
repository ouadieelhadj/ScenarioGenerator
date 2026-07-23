package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.common.iso.IsoDump;

import com.staging.sg.common.entity.McDmasInterface;
import com.staging.sg.common.iso.McDmasLengthChannel;
import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.service.McDmasInterfaceService;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Liaisons permanentes du MEMBRE vers le reseau Mastercard DMAS.
 *
 * ------------------------------------------------------------------
 *  UNE LIAISON PAR BANQUE, DANS LA MEME JVM
 * ------------------------------------------------------------------
 *     --sg.interface=DMAS_BANK_A                 une liaison
 *     --sg.interface=DMAS_BANK_A,DMAS_BANK_B     deux liaisons
 *
 * Chaque banque possede sa PROPRE connexion, avec son channel, son
 * thread d'ecoute, sa correlation par STAN et son etat de sign-on :
 *
 *     022905 -> channel, listener, pending, signedOn --socket--> MC_1:8500
 *     022906 -> channel, listener, pending, signedOn --socket--> MC_2:8503
 *
 * Les sockets restent ouvertes en continu et sont independantes : si
 * l'une tombe, l'autre continue.
 *
 * Chaque methode existe en deux formes, avec et sans banque. Sans
 * banque, c'est l'interface principale qui repond — les classes
 * appelantes existantes fonctionnent donc sans modification.
 */
@Component
public class McDmasMemberClient {

    private static final Logger log = LoggerFactory.getLogger(McDmasMemberClient.class);

    private final McDmasInterfaceService iface;

    /** @Lazy : le service prend ce client au constructeur, cycle Spring sinon. */
    @Autowired @Lazy private McDmasKeyExchange keyExchange;

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public McDmasMemberClient(McDmasInterfaceService iface) {
        this.iface = iface;
    }

    // ==================================================================
    //  UNE CONNEXION = UNE LIAISON PERMANENTE
    // ==================================================================

    private class Connection {
        final String bankCode;
        final McDmasInterface cfg;

        McPackagerEbcdic    packager;
        McDmasLengthChannel channel;
        Thread              listener;
        volatile boolean    running  = false;
        volatile boolean    signedOn = false;

        final AtomicInteger stanSeq = new AtomicInteger(1);
        final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

        Connection(McDmasInterface cfg) {
            this.cfg = cfg;
            this.bankCode = cfg.getBankCode();
        }

        String host() {
            String h = cfg.getTargetHost();
            return (h != null && !h.isBlank()) ? h : "localhost";
        }

        int port() {
            Integer p = cfg.getTargetPort();
            return (p != null) ? p : 8500;
        }

        synchronized void ensureConnected() throws Exception {
            if (channel != null && channel.isConnected()) return;

            String h = host();
            int    p = port();
            try {
                packager = new McPackagerEbcdic();
                channel  = new McDmasLengthChannel();
                channel.setPackager(packager);
                channel.setHost(h, p);
                log.info("[JPOS-CLI:{}] Liaison permanente -> {}:{}", bankCode, h, p);
                channel.connect();
                startListener();
            } catch (Exception e) {
                channel = null;
                log.error("[JPOS-CLI:{}] Reseau injoignable sur {}:{} — {}",
                        bankCode, h, p, e.getMessage());
                throw new IllegalStateException(
                        "Reseau Mastercard DMAS indisponible sur " + h + ":" + p
                      + " pour la banque " + bankCode, e);
            }
        }

        void startListener() {
            running = true;
            listener = new Thread(() -> {
                while (running) {
                    try {
                        { org.jpos.iso.ISOMsg __rx = channel.receive(); IsoDump.dump("MEMBRE", "RECEPTION", __rx); handleIncoming(this, __rx); }
                    } catch (Exception e) {
                        if (running) {
                            log.error("[JPOS-CLI:{}] Erreur d'ecoute : {}", bankCode, e.getMessage());
                        }
                        running = false;
                        signedOn = false;
                    }
                }
                log.info("[JPOS-CLI:{}] Listener arrete", bankCode);
            }, "mc-dmas-listener-" + bankCode);
            listener.setDaemon(true);
            listener.start();
        }

        void close() {
            running = false;
            signedOn = false;
            try {
                if (channel != null) channel.disconnect();
            } catch (Exception ignore) { }
        }

        boolean isConnected() {
            return channel != null && channel.isConnected();
        }

        String nextStan() {
            return String.format("%06d", stanSeq.getAndIncrement() % 1_000_000);
        }
    }

    // ==================================================================
    //  RESOLUTION
    // ==================================================================

    private Connection conn(String bankCode) {
        McDmasInterface cfg = iface.byBank(bankCode);
        return connections.computeIfAbsent(cfg.getBankCode(), b -> new Connection(cfg));
    }

    // ==================================================================
    //  CYCLE DE VIE
    // ==================================================================

    /** Ouvre les liaisons de toutes les banques pilotees. */
    public void start() {
        for (McDmasInterface i : iface.all()) {
            try {
                conn(i.getBankCode()).ensureConnected();
            } catch (Exception e) {
                log.warn("[JPOS-CLI:{}] Connexion differee : {}",
                        i.getBankCode(), e.getMessage());
            }
        }
    }

    public void start(String bankCode) throws Exception {
        conn(bankCode).ensureConnected();
    }

    @PreDestroy
    public void stop() {
        connections.forEach((bank, c) -> {
            c.close();
            iface.setStatus(bank, McDmasInterfaceService.OFF);
            log.info("[JPOS-CLI:{}] Deconnecte", bank);
        });
    }

    // ==================================================================
    //  ROUTAGE DES MESSAGES ENTRANTS
    // ==================================================================

    private void handleIncoming(Connection c, ISOMsg m) {
        try {
            String stan = m.hasField(11) ? m.getString(11) : null;
            CompletableFuture<ISOMsg> fut = (stan != null) ? c.pending.remove(stan) : null;
            if (fut != null) {
                fut.complete(m);
                return;
            }

            String mti  = m.getMTI();
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            log.info("[JPOS-CLI:{}] Message POUSSE par le reseau : MTI={} DE70={} STAN={}",
                    c.bankCode, mti, de70, stan);

            if ("0800".equals(mti)) {
                String rc = "161".equals(de70)
                        ? keyExchange.handleKeyDelivery(c.bankCode, m)
                        : "00";
                sendAck(c, m, rc);
            } else if ("0820".equals(mti)) {
                log.info("[JPOS-CLI:{}] Advice 0820 DE70={} recu", c.bankCode, de70);
                if ("161".equals(de70)) {
                    keyExchange.handleKeyAcknowledgement(c.bankCode, m);
                }
            } else {
                log.warn("[JPOS-CLI:{}] MTI pousse non gere : {}", c.bankCode, mti);
            }
        } catch (Exception e) {
            log.error("[JPOS-CLI:{}] Erreur de routage : {}", c.bankCode, e.getMessage(), e);
        }
    }

    private void sendAck(Connection c, ISOMsg req, String de39) throws Exception {
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
        IsoDump.dump("MEMBRE", "ENVOI", r); c.channel.send(r);
        log.info("[JPOS-CLI:{}] Accuse 0810 DE39={} envoye", c.bankCode, de39);
    }

    // ==================================================================
    //  API — forme par defaut et forme par banque
    // ==================================================================

    public boolean hasActiveSession() { return hasActiveSession(null); }

    public boolean hasActiveSession(String bankCode) {
        Connection c = conn(bankCode);
        return c.isConnected() && c.signedOn;
    }

    public String getActiveMemberGroupId() { return getActiveMemberGroupId(null); }

    public String getActiveMemberGroupId(String bankCode) {
        Connection c = conn(bankCode);
        return c.signedOn ? c.cfg.getGroupSignonDe2() : null;
    }

    /** Cle d'indexation des cles en base — a ne pas confondre avec le DE2. */
    public String memberGroupId(String bankCode) {
        return conn(bankCode).cfg.getMemberGroupId();
    }

    public boolean isConnected() { return isConnected(null); }

    public boolean isConnected(String bankCode) {
        return conn(bankCode).isConnected();
    }

    public ISOMsg pushAndWait(ISOMsg msg, int timeoutSeconds) throws Exception {
        return pushAndWait(null, msg, timeoutSeconds);
    }

    public ISOMsg pushAndWait(String bankCode, ISOMsg msg, int timeoutSeconds) throws Exception {
        Connection c = conn(bankCode);
        c.ensureConnected();

        String stan = msg.hasField(11) ? msg.getString(11) : null;
        if (stan == null) {
            throw new IllegalStateException("DE11 (STAN) requis pour correler la reponse");
        }

        CompletableFuture<ISOMsg> fut = new CompletableFuture<>();
        c.pending.put(stan, fut);
        try {
            IsoDump.dump("MEMBRE", "ENVOI", msg); c.channel.send(msg);
            return fut.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.io.IOException e) {
            c.signedOn = false;
            log.error("[JPOS-CLI:{}] Liaison rompue : {}", c.bankCode, e.getMessage());
            throw new IllegalStateException(
                    "Liaison rompue avec le reseau pour " + c.bankCode
                  + " — refaire un sign-on", e);
        } finally {
            c.pending.remove(stan);
        }
    }

    public void pushOnActiveSession(ISOMsg msg) throws Exception {
        pushOnActiveSession(null, msg);
    }

    public void pushOnActiveSession(String bankCode, ISOMsg msg) throws Exception {
        Connection c = conn(bankCode);
        c.ensureConnected();
        IsoDump.dump("MEMBRE", "ENVOI", msg); c.channel.send(msg);
    }

    // ==================================================================
    //  SIGN-ON / ECHO / SIGN-OFF
    // ==================================================================

    public Map<String, Object> signOn() throws Exception { return signOn(null); }

    public synchronized Map<String, Object> signOn(String bankCode) throws Exception {
        Connection c = conn(bankCode);
        c.ensureConnected();
        Map<String, Object> r = sendNetworkMessage(c, "061", "SIGN-ON");
        c.signedOn = Boolean.TRUE.equals(r.get("success"));
        if (c.signedOn) {
            log.info("[JPOS-CLI:{}] Sign-on accepte (memberGroup={})",
                    c.bankCode, c.cfg.getMemberGroupId());
            iface.setStatus(c.bankCode, McDmasInterfaceService.SIGNON);
        }
        return r;
    }

    public Map<String, Object> echoTest() throws Exception { return echoTest(null); }

    public Map<String, Object> echoTest(String bankCode) throws Exception {
        Connection c = conn(bankCode);
        c.ensureConnected();
        return sendNetworkMessage(c, "270", "ECHO-TEST");
    }

    public Map<String, Object> signOff() throws Exception { return signOff(null); }

    public Map<String, Object> signOff(String bankCode) throws Exception {
        Connection c = conn(bankCode);
        c.ensureConnected();
        Map<String, Object> r = sendNetworkMessage(c, "062", "SIGN-OFF");
        c.signedOn = false;
        iface.setStatus(c.bankCode, McDmasInterfaceService.SIGNOFF);
        return r;
    }

    /** Keep-alive : un echo par liaison etablie. */
    @Scheduled(fixedRateString = "${dmas.jpos.echo-interval-ms:60000}")
    public void scheduledEcho() {
        connections.forEach((bank, c) -> {
            if (!c.running || !c.signedOn) return;
            try {
                Map<String, Object> r = echoTest(bank);
                log.debug("[JPOS-CLI:{}] Keep-alive : {}", bank, r.get("success"));
            } catch (Exception e) {
                log.warn("[JPOS-CLI:{}] Keep-alive echoue : {}", bank, e.getMessage());
            }
        });
    }

    private Map<String, Object> sendNetworkMessage(Connection c, String de70, String label)
            throws Exception {
        String stan = c.nextStan();
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg m = new ISOMsg();
        m.setPackager(c.packager);
        m.setMTI("0800");
        m.set(2,  c.cfg.getGroupSignonDe2());
        m.set(7,  dt);
        m.set(11, stan);
        m.set(33, c.cfg.getFwdIdDe33());
        m.set(70, de70);
        m.set(94, "0B0    ");
        m.set(96, "00000000");

        log.info("[JPOS-CLI:{}] {} -> 0800 DE70={} STAN={}", c.bankCode, label, de70, stan);
        ISOMsg resp = pushAndWait(c.bankCode, m, 15);

        String rc = resp.hasField(39) ? resp.getString(39) : "??";
        boolean ok = "00".equals(rc);
        log.info("[JPOS-CLI:{}] {} <- 0810 DE39={} ok={}", c.bankCode, label, rc, ok);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bank_code",    c.bankCode);
        r.put("label",        label);
        r.put("mti_sent",     "0800");
        r.put("de070",        de70);
        r.put("stan",         stan);
        r.put("mti_received", resp.getMTI());
        r.put("de039",        rc);
        r.put("success",      ok);
        return r;
    }
}
