package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.SwamDe48;
import com.staging.sg.common.iso.SwamPackager;
import com.staging.sg.common.iso.SwamLengthChannel;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.service.SwamInterfaceService;
import com.staging.sg.common.repository.SwamAcqKeyRepository;
import com.staging.sg.common.repository.SwamKekRepository;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client jPOS cote Membre/banque (SWAM acquirer).
 * Liaison PERMANENTE bidirectionnelle : le switch peut aussi ecrire.
 *
 * Flux conforme spec HPS (logs reels) :
 *   1. Membre -> Switch : 1804 DE24=801 (Sign-on)
 *   2. Switch -> Membre : 1814 DE39=800 (Reponse sign-on)
 *   3. Switch -> Membre : 1804 DE24=811 (ZPK poussee, DE48=X<cle>)  <-- SPONTANE
 *   4. Membre -> Switch : 1814 DE39=800 (Accuse reception ZPK)
 */
@Component
public class SwamJposClient {

    private static final Logger log = LoggerFactory.getLogger(SwamJposClient.class);

    private final SwamInterfaceService interfaceService;
    private final SwamPackager packager = new SwamPackager();
    private SwamLengthChannel channel;

    // Injectes en @Autowired pour eviter dependance circulaire au constructeur
    @Autowired private JposHsmService hsm;
    @Autowired private SwamKekRepository kekRepo;
    @Autowired private SwamAcqKeyRepository acqKeyRepo;

    private final ConcurrentHashMap<String, ISOMsg> responses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private Thread receiver;
    private volatile boolean running = false;

    public SwamJposClient(SwamInterfaceService interfaceService) {
        this.interfaceService = interfaceService;
    }

    private String host() {
        String host = interfaceService.get().getTargetHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] target_host obligatoire");
        }
        return host;
    }

    private int port() {
        Integer port = interfaceService.get().getTargetPort();
        if (port == null) {
            throw new IllegalStateException("[SWAM-IF] target_port obligatoire");
        }
        return port;
    }

    private String memberGroupId() {
        String value = interfaceService.get().getMemberGroupId();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[SWAM-IF] member_group_id obligatoire");
        }
        return value;
    }

    /** Etablit la connexion permanente si pas deja connectee. */
    public synchronized void connect() throws Exception {
        if (channel != null && channel.isConnected()) return;
        String h = host(); int p = port();
        channel = new SwamLengthChannel(packager);
        channel.setHost(h);
        channel.setPort(p);
        channel.connect();
        running = true;
        startReceiver();
        log.info("[SWAM-CLI] Connecte au switch {}:{}", h, p);
    }

    /**
     * Receiver : traite les messages entrants.
     * - 1814 (reponse) -> correlation par STAN via latch
     * - 1804 (message spontane du switch) -> key push, traite immediatement
     */
    private void startReceiver() {
        receiver = new Thread(() -> {
            while (running && channel != null && channel.isConnected()) {
                try {
                    ISOMsg msg = channel.receive();
                    String mti  = msg.getMTI();
                    String stan = msg.hasField(11) ? msg.getString(11) : null;

                    // ── Message SPONTANE du switch (1804) ────────────────────
                    if ("1804".equals(mti)) {
                        String func = msg.hasField(24) ? msg.getString(24) : "?";
                        log.info("[SWAM-CLI] Message SPONTANE du switch : 1804 DE24={} STAN={}", func, stan);
                        handleSwitchPush(msg, func);
                        continue;
                    }

                    // ── Reponse correlee par STAN (1814, 1110, ...) ──────────
                    if (stan != null) {
                        responses.put(stan, msg);
                        CountDownLatch l = latches.get(stan);
                        if (l != null) l.countDown();
                    }
                } catch (Exception e) {
                    if (running) log.warn("[SWAM-CLI] receiver: {}", e.getMessage());
                    break;
                }
            }
        }, "swam-client-receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    /**
     * Traite un message pousse par le switch (1804 DE24=811/899).
     * Importe la cle sous LMK local, persiste, puis repond 1814 DE39=800.
     */
    private void handleSwitchPush(ISOMsg msg, String func) {
        try {
            if (!"811".equals(func) && !"899".equals(func)) {
                log.warn("[SWAM-CLI] 1804 DE24={} non gere (pas un key push)", func);
                sendAck(msg, "800");
                return;
            }

            String keyType = "811".equals(func) ? "PEK" : "MAK";
            String tagKey  = "811".equals(func) ? SwamDe48.TAG_ZPK : SwamDe48.TAG_ZAK;

            String de48 = msg.hasField(48) ? msg.getString(48) : null;
            if (de48 == null) {
                log.error("[SWAM-CLI] Key push sans DE48 -> rejet");
                sendAck(msg, "909");
                return;
            }

            SwamDe48 parsed = SwamDe48.parse(de48);
            String keyRaw = parsed.get(tagKey);
            if (keyRaw == null) {
                log.error("[SWAM-CLI] Tag {} absent du DE48 -> rejet", tagKey);
                sendAck(msg, "909");
                return;
            }

            // Retirer le prefixe X (format HPS : X<cle hex>)
            String keyUnderKekHex = keyRaw.startsWith("X") ? keyRaw.substring(1) : keyRaw;
            int keyLen = keyUnderKekHex.length() / 2;
            log.info("[SWAM-CLI] {} recue du switch ({} hex, {} octets)", keyType, keyUnderKekHex.length(), keyLen);

            SwamKek kek = kekRepo.findByMemberGroupId(memberGroupId()).orElse(null);
            if (kek == null || kek.getKekClear() == null) {
                log.error("[SWAM-CLI] KEK absente -> impossible d'importer la cle (bootstrap d'abord)");
                sendAck(msg, "909");
                return;
            }

            // Import sous LMK local.
            // IMPORTANT : la longueur est decidee par la CLE RECUE, pas par le code
            // fonction. Le switch REEL pousse une ZAK de 16 octets (double longueur)
            // via le 899 — l'ancien code forcait importWorkingKeySingle (8 octets),
            // d'ou "DES key too long - should be 8 bytes".
            HsmService.KeyResult imp = (keyLen >= 16)
                    ? hsm.importWorkingKey(keyType, keyUnderKekHex, kek.getKekClear(), keyLen)
                    : hsm.importWorkingKeySingle(keyType, keyUnderKekHex, kek.getKekClear());
            log.info("[SWAM-CLI] Import {} en {} longueur ({} octets)",
                    keyType, (keyLen >= 16 ? "DOUBLE" : "SIMPLE"), keyLen);

            // Persister
            SwamAcqKey ak = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(
                            memberGroupId(), keyType, "ACTIVE")
                    .orElseGet(SwamAcqKey::new);
            ak.setMemberGroupId(memberGroupId());
            ak.setKeyType(keyType);
            ak.setKeyLength(keyLen);
            ak.setKeyUnderLmk(imp.keyUnderLmkHex);
            ak.setKeyUnderKek(keyUnderKekHex.length() > 64 ? keyUnderKekHex.substring(0,64) : keyUnderKekHex);
            ak.setKcv(imp.kcv);
            ak.setStatus("ACTIVE");
            acqKeyRepo.save(ak);
            log.info("[SWAM-CLI] {} importee+persistee (KCV={})", keyType, imp.kcv);

            // Accuse reception 1814 DE39=800
            sendAck(msg, "800");

        } catch (Exception e) {
            log.error("[SWAM-CLI] handleSwitchPush erreur : {}", e.getMessage(), e);
            try { sendAck(msg, "909"); } catch (Exception ignore) {}
        }
    }

    /** Envoie un 1814 en reponse au message pousse par le switch. */
    private void sendAck(ISOMsg req, String de39) throws Exception {
        ISOMsg ack = new ISOMsg();
        ack.setPackager(packager);
        ack.setMTI("1814");
        ack.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
        if (req.hasField(11)) ack.set(11, req.getString(11));
        if (req.hasField(24)) ack.set(24, req.getString(24));
        ack.set(39, de39);
        channel.send(ack);
        log.info("[SWAM-CLI] Accuse reception envoye : 1814 DE39={}", de39);
    }

    /** Envoie un message et attend la reponse correlee par STAN (timeout en secondes). */
    public ISOMsg sendAndWait(ISOMsg req, int timeoutSeconds) throws Exception {
        connect();
        String stan = req.getString(11);
        CountDownLatch latch = new CountDownLatch(1);
        latches.put(stan, latch);
        channel.send(req);
        boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
        latches.remove(stan);
        ISOMsg resp = responses.remove(stan);
        if (!ok || resp == null) throw new RuntimeException("Timeout SWAM (STAN=" + stan + ")");
        return resp;
    }

    public boolean isConnected() { return channel != null && channel.isConnected(); }
    public SwamPackager getPackager() { return packager; }

    @PreDestroy
    public void disconnect() {
        running = false;
        try { if (channel != null) channel.disconnect(); } catch (Exception ignore) {}
        log.info("[SWAM-CLI] Deconnecte");
    }
}
