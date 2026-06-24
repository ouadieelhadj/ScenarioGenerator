package com.staging.sg.dmas.issuer.issuer;

import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.iso.DmasLengthChannel;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import com.staging.sg.common.entity.DmasKek;
import com.staging.sg.common.entity.DmasIssKey;
import com.staging.sg.common.repository.DmasKekRepository;
import com.staging.sg.common.repository.DmasIssKeyRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client jPOS cote ISSUER (= customer/membre).
 * Connexion PERMANENTE vers l'acquereur : connecte une seule fois (sign-on),
 * ne se deconnecte jamais. Un thread d'ecoute continu lit tout ce qui arrive
 * (reponses attendues + messages pousses par l'acquereur comme le PEK exchange).
 * Corrélation requete/reponse par STAN via CompletableFuture.
 */
@Component
public class DmasJposClient {

    private static final Logger log = LoggerFactory.getLogger(DmasJposClient.class);

    @Value("${dmas.jpos.acquirer-host:localhost}")
    private String acquirerHost;

    @Value("${dmas.jpos.acquirer-port:8600}")
    private int acquirerPort;

    @Value("${dmas.member-group:TESTGRP01}")
    private String memberGroup;

    @Value("${dmas.jpos.group-signon-id:40260}")
    private String groupSignonId;

    @Value("${dmas.jpos.forwarding-id:011901}")
    private String forwardingId;

    private final AtomicInteger stanSeq = new AtomicInteger(1);

    private final JposHsmService hsm;
    private final DmasKekRepository kekRepo;
    private final DmasIssKeyRepository issKeyRepo;

    public DmasJposClient(JposHsmService hsm, DmasKekRepository kekRepo, DmasIssKeyRepository issKeyRepo) {
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.issKeyRepo = issKeyRepo;
    }

    private DmasLengthChannel channel;
    private McPackagerEbcdic  packager;
    private Thread            listenerThread;
    private volatile boolean  running = false;

    /** Correlation requete/reponse : STAN -> future en attente de la reponse. */
    private final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

    /** Construit et envoie un 0800 sign-on conforme, retourne le resultat. Connecte si besoin. */
    public synchronized Map<String,Object> signOn() throws Exception {
        ensureConnected();
        return sendAndWait("061", "SIGN-ON");
    }

    public Map<String,Object> echoTest() throws Exception {
        ensureConnected();
        return sendAndWait("270", "ECHO-TEST");
    }

    /** Echo automatique toutes les 60s pour garder la connexion vivante (keep-alive). */
    @Scheduled(fixedRateString = "${dmas.jpos.echo-interval-ms:60000}")
    public void scheduledEcho() {
        if (!running) return; // pas encore connecte (pas de sign-on fait)
        try {
            Map<String,Object> r = echoTest();
            log.info("[JPOS-CLI] Keep-alive echo OK : {}", r.get("success"));
        } catch (Exception e) {
            log.warn("[JPOS-CLI] Keep-alive echo echoue : {}", e.getMessage());
        }
    }

    private void ensureConnected() throws Exception {
        if (channel != null && channel.isConnected()) return;
        try {
            packager = new McPackagerEbcdic();
            channel = new DmasLengthChannel();
            channel.setPackager(packager);
            channel.setHost(acquirerHost, acquirerPort);
            log.info("[JPOS-CLI] Connexion permanente -> {}:{}", acquirerHost, acquirerPort);
            channel.connect();
            startListener();
        } catch (Exception e) {
            channel = null;
            log.error("[JPOS-CLI] Acquereur non joignable sur {}:{} - {} (verifier que le module acquereur est demarre)",
                    acquirerHost, acquirerPort, e.getMessage());
            throw new IllegalStateException(
                "Acquereur indisponible sur " + acquirerHost + ":" + acquirerPort
                + " - demarrer le module acquereur puis reessayer", e);
        }
    }

    /** Thread d'ecoute continu : lit tout ce qui arrive sur la connexion permanente. */
    private void startListener() {
        running = true;
        listenerThread = new Thread(() -> {
            while (running) {
                try {
                    ISOMsg m = channel.receive();
                    handleIncoming(m);
                } catch (Exception e) {
                    if (running) log.error("[JPOS-CLI] Erreur ecoute : {}", e.getMessage());
                    running = false;
                }
            }
        }, "dmas-jpos-client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /** Route un message recu : soit une reponse attendue (STAN connu), soit un message pousse. */
    private void handleIncoming(ISOMsg m) {
        try {
            String stan = m.hasField(11) ? m.getString(11) : null;
            CompletableFuture<ISOMsg> fut = (stan != null) ? pending.remove(stan) : null;
            if (fut != null) {
                fut.complete(m);
                return;
            }
            // Message non sollicite (push de l'acquereur, ex: PEK exchange system-generated)
            String mti = m.getMTI();
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            log.info("[JPOS-CLI] Message POUSSE recu (non sollicite) : MTI={} DE70={} STAN={}", mti, de70, stan);
            if ("0800".equals(mti)) {
                handlePushedNetworkMessage(m, de70);
            } else if ("0820".equals(mti)) {
                String result = "164".equals(de70) ? "SUCCES" : "ECHEC";
                log.info("[JPOS-CLI] 0820 PEK exchange advice recu : DE70={} ({})", de70, result);
            } else {
                log.warn("[JPOS-CLI] MTI pousse non gere : {}", mti);
            }
        } catch (Exception e) {
            log.error("[JPOS-CLI] Erreur routage message recu : {}", e.getMessage(), e);
        }
    }

    /** Traite un 0800 pousse par l'acquereur (ex: PEK exchange DE70=161) et repond 0810. */
    private void handlePushedNetworkMessage(ISOMsg m, String de70) {
        try {
            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("0810");
            if (m.hasField(2))  r.set(2,  m.getString(2));
            r.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(33)) r.set(33, m.getString(33));
            r.set(39, "00");
            if (m.hasField(70)) r.set(70, m.getString(70));

            channel.send(r);
            log.info("[JPOS-CLI] Repondu 0810 DE39=00 au message pousse (DE70={})", de70);

            if ("161".equals(de70) && m.hasField(48)) {
                processReceivedPek(m);
            }
        } catch (Exception e) {
            log.error("[JPOS-CLI] Erreur reponse au message pousse : {}", e.getMessage(), e);
        }
    }

    /** Dechiffre la PEK recue (DE48), verifie le KCV, et la stocke sous LMK issuer. */
    private void processReceivedPek(ISOMsg m) {
        try {
            String de48 = m.getString(48);
            KeyExchangeBlock keb = KeyExchangeBlock.parseDe48(de48);
            log.info("[JPOS-CLI] DE48 parse : keyClass={} index={} cycle={} kcv_recu={}",
                    keb.keyClassId, keb.keyIndex, keb.keyCycle, keb.kcv);

            DmasKek kek = kekRepo.findByMemberGroupId(memberGroup)
                    .orElseThrow(() -> new IllegalStateException("KEK introuvable pour " + memberGroup));
            if (kek.getKekClear() == null)
                throw new IllegalStateException("kek_clear absent pour " + memberGroup);

            int keyLen = (kek.getKeyLength() != null) ? kek.getKeyLength() : 24;

            // Dechiffre sous KEK, reforme sous LMK issuer
            JposHsmService.KeyResult result = hsm.importWorkingKey("PEK", keb.encryptedKeyHex, kek.getKekClear(), keyLen);

            boolean kcvMatch = result.kcv != null && keb.kcv != null
                    && result.kcv.equalsIgnoreCase(keb.kcv.substring(0, Math.min(6, keb.kcv.length())));
            log.info("[JPOS-CLI] PEK dechiffree : KCV calcule={} KCV recu={} match={}", result.kcv, keb.kcv, kcvMatch);

            if (!kcvMatch) {
                log.error("[JPOS-CLI] KCV mismatch - PEK NON stockee (integrite non verifiee)");
                return;
            }

            DmasIssKey ik = issKeyRepo.findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                    .orElseGet(DmasIssKey::new);
            ik.setMemberGroupId(memberGroup);
            ik.setKeyType("PEK");
            ik.setKeyLength(keyLen);
            ik.setKeyUnderLmk(result.keyUnderLmkHex);
            ik.setKeyUnderKek(result.keyUnderKekHex.length() > 64 ? result.keyUnderKekHex.substring(0,64) : result.keyUnderKekHex);
            ik.setKcv(result.kcv);
            ik.setStatus("ACTIVE");
            issKeyRepo.save(ik);
            log.info("[JPOS-CLI] Nouvelle PEK persistee dans dmas_iss_keys (KCV={})", result.kcv);
        } catch (Exception e) {
            log.error("[JPOS-CLI] Echec traitement PEK recue : {}", e.getMessage(), e);
        }
    }

    /** Envoie un 0800 et attend SA reponse (correlee par STAN), avec timeout. */
    private Map<String,Object> sendAndWait(String de070, String label) throws Exception {
        String stan = String.format("%07d", stanSeq.getAndIncrement());  // n-7 conforme DE011 (pas de padding implicite)
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        m.setMTI("0800");
        m.set(2,  groupSignonId);
        m.set(7,  dt);
        m.set(11, stan);
        m.set(33, forwardingId);
        m.set(70, de070);
        m.set(94, "0I0    ");
        m.set(96, "000000");

        CompletableFuture<ISOMsg> fut = new CompletableFuture<>();
        pending.put(stan, fut);

        log.info("[JPOS-CLI] {} -> envoi 0800 DE70={} STAN={} memberGroup={}", label, de070, stan, memberGroup);
        channel.send(m);

        ISOMsg resp;
        try {
            resp = fut.get(15, TimeUnit.SECONDS);
        } finally {
            pending.remove(stan);
        }

        String rc = resp.hasField(39) ? resp.getString(39) : "??";
        boolean ok = "00".equals(rc);
        log.info("[JPOS-CLI] {} <- 0810 DE39={} ok={}", label, rc, ok);

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("label", label);
        r.put("mti_sent", "0800");
        r.put("de070", de070);
        r.put("stan", stan);
        r.put("mti_received", resp.getMTI());
        r.put("de039", rc);
        r.put("success", ok);
        return r;
    }
}
