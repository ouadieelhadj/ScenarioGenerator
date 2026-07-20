package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMastercardKey;
import com.staging.sg.common.entity.NetworkRef;
import com.staging.sg.common.iso.McDmasLengthChannel;
import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import com.staging.sg.common.repository.McDmasKekRepository;
import com.staging.sg.common.repository.McDmasMastercardKeyRepository;
import com.staging.sg.common.repository.NetworkRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Serveur jPOS cote RESEAU MASTERCARD DMAS (simule).
 *
 * LIAISON PERMANENTE : l'ISOServer accepte la connexion du membre et
 * conserve la session (ISOSource) pour pouvoir y ECRIRE a tout moment.
 *
 * Meme schema que SwamJposServer et McSmsJposServer.
 *
 * ------------------------------------------------------------------
 *  REMPLACE McDmasMastercardServer
 * ------------------------------------------------------------------
 * Avant, ce module etait client : il se connectait au membre et lui
 * envoyait le sign-on. C'etait l'inverse de la realite. Desormais :
 *
 *     membre  ---- se connecte ---->  reseau Mastercard (ce module)
 *     membre  ---- 0800/061 ------->  0810 DE39=00
 *     membre  ---- 0100 ----------->  0110 (decision)
 *     membre  ---- 0800/161 ------->  0810 + import de la PEK
 *
 * Le SENS DE L'ECHANGE DE CLES est inchange pour l'instant : c'est
 * toujours le membre qui pousse la PEK. Ce point sera revu separement,
 * specifications DMAS en main.
 */
@Component
public class McDmasMastercardServer {

    private static final Logger log = LoggerFactory.getLogger(McDmasMastercardServer.class);

    private static final String NETWORK_CODE     = "DMAS";
    private static final int    DEFAULT_ISO_PORT = 8500;

    private final NetworkRepository networkRepository;
    private final JposHsmService hsm;
    private final McDmasKekRepository kekRepo;
    private final McDmasMastercardKeyRepository issKeyRepo;
    private final McDmasMastercardHandler handler;

    @Value("${dmas.member-group-id:TESTGRP01}")
    private String memberGroup;

    private ISOServer isoServer;
    private Thread    serverThread;

    /** Session du membre, conservee pour pouvoir pousser des messages. */
    private volatile ISOSource activeMemberSession;
    private volatile String    activeMemberGroupId;
    private final Object sendLock = new Object();

    /** Correlation des reponses a NOS propres push. */
    private final Map<String, CompletableFuture<ISOMsg>> pending = new ConcurrentHashMap<>();

    public McDmasMastercardServer(NetworkRepository networkRepository,
                                  JposHsmService hsm,
                                  McDmasKekRepository kekRepo,
                                  McDmasMastercardKeyRepository issKeyRepo,
                                  @Lazy McDmasMastercardHandler handler) {
        this.networkRepository = networkRepository;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.issKeyRepo = issKeyRepo;
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
            log.warn("[JPOS-SRV] Lecture port KO ({}), fallback {}", e.getMessage(), DEFAULT_ISO_PORT);
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
            log.error("[JPOS-SRV] Echec demarrage : {}", e.getMessage(), e);
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
    //  LISTENER
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

                if ("0800".equals(mti)) return handleNetwork(source, m);
                if ("0100".equals(mti)) return handleAuthorization(source, m);
                if ("0200".equals(mti)) return handleAuthorization(source, m);

                log.warn("[JPOS-SRV] MTI non gere : {}", mti);
                return false;

            } catch (Exception e) {
                log.error("[JPOS-SRV] Erreur de traitement : {}", e.getMessage(), e);
                return false;
            }
        }

        /** 0800 : sign-on, echo, sign-off, echange de cles. */
        private boolean handleNetwork(ISOSource source, ISOMsg m) throws Exception {
            String de70 = m.hasField(70) ? m.getString(70) : "?";
            String stan = m.hasField(11) ? m.getString(11) : "?";

            String label = switch (de70) {
                case "061" -> "SIGN-ON";
                case "062" -> "SIGN-OFF";
                case "270" -> "ECHO TEST";
                case "161" -> "ECHANGE DE CLE";
                default    -> "FONCTION " + de70;
            };
            log.info("[JPOS-SRV] {} recu (DE70={} STAN={})", label, de70, stan);

            // Enregistrement de la session au sign-on
            if ("061".equals(de70)) {
                activeMemberSession = source;
                activeMemberGroupId = m.hasField(2) ? m.getString(2) : memberGroup;
                log.info("[JPOS-SRV] Session membre enregistree (id={})", activeMemberGroupId);
            } else if ("062".equals(de70)) {
                log.info("[JPOS-SRV] Sign-off — session liberee");
                activeMemberSession = null;
                activeMemberGroupId = null;
            } else if (activeMemberSession == null) {
                // Toute autre fonction reactive la session si besoin
                activeMemberSession = source;
            }

            ISOMsg r = new ISOMsg();
            r.setPackager(m.getPackager());
            r.setMTI("0810");
            if (m.hasField(2))  r.set(2,  m.getString(2));
            r.set(7, new SimpleDateFormat("MMddHHmmss").format(new Date()));
            if (m.hasField(11)) r.set(11, m.getString(11));
            if (m.hasField(33)) r.set(33, m.getString(33));
            r.set(39, "00");
            r.set(63, "MCC000NPQ");
            if (m.hasField(70)) r.set(70, m.getString(70));

            source.send(r);
            log.info("[JPOS-SRV] Repondu 0810 DE39=00 ({})", label);

            // Echange de cles : le membre nous livre une PEK dans le DE48
            if ("161".equals(de70) && m.hasField(48)) {
                processReceivedPek(m);
            }
            return true;
        }

        /** 0100 / 0200 : decision d'autorisation. */
        private boolean handleAuthorization(ISOSource source, ISOMsg m) throws Exception {
            String stan = m.hasField(11) ? m.getString(11) : "?";
            log.info("[JPOS-SRV] Autorisation {} recue (STAN={})", m.getMTI(), stan);

            if (activeMemberSession == null) activeMemberSession = source;

            ISOMsg resp = handler.buildAuthResponse(m);
            source.send(resp);
            log.info("[JPOS-SRV] Repondu {} DE39={} STAN={}",
                    resp.getMTI(),
                    resp.hasField(39) ? resp.getString(39) : "?",
                    stan);
            return true;
        }
    }

    // ====================================================================
    //  ECHANGE DE CLES — reception de la PEK
    // ====================================================================

    /** Dechiffre la PEK recue (DE48), verifie le KCV, la stocke sous LMK. */
    private void processReceivedPek(ISOMsg m) {
        try {
            String de48 = m.getString(48);
            KeyExchangeBlock keb = KeyExchangeBlock.parseDe48(de48);
            log.info("[JPOS-SRV] DE48 parse : keyClass={} index={} cycle={} kcv_recu={}",
                    keb.keyClassId, keb.keyIndex, keb.keyCycle, keb.kcv);

            McDmasKek kek = kekRepo.findByMemberGroupId(memberGroup)
                    .orElseThrow(() -> new IllegalStateException("KEK introuvable pour " + memberGroup));
            if (kek.getKekClear() == null) {
                throw new IllegalStateException("kek_clear absent pour " + memberGroup);
            }

            int keyLen = (kek.getKeyLength() != null) ? kek.getKeyLength() : 24;

            JposHsmService.KeyResult result =
                    hsm.importWorkingKey("PEK", keb.encryptedKeyHex, kek.getKekClear(), keyLen);

            boolean kcvMatch = result.kcv != null && keb.kcv != null
                    && result.kcv.equalsIgnoreCase(keb.kcv.substring(0, Math.min(6, keb.kcv.length())));
            log.info("[JPOS-SRV] PEK dechiffree : KCV calcule={} recu={} match={}",
                    result.kcv, keb.kcv, kcvMatch);

            if (!kcvMatch) {
                log.error("[JPOS-SRV] KCV different — PEK NON stockee");
                return;
            }

            McDmasMastercardKey ik = issKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroup, "PEK", "ACTIVE")
                    .orElseGet(McDmasMastercardKey::new);
            ik.setMemberGroupId(memberGroup);
            ik.setKeyType("PEK");
            ik.setKeyLength(keyLen);
            ik.setKeyUnderLmk(result.keyUnderLmkHex);
            ik.setKeyUnderKek(result.keyUnderKekHex.length() > 64
                    ? result.keyUnderKekHex.substring(0, 64)
                    : result.keyUnderKekHex);
            ik.setKcv(result.kcv);
            ik.setStatus("ACTIVE");
            issKeyRepo.save(ik);
            log.info("[JPOS-SRV] PEK persistee dans mc_dmas_mastercard_keys (KCV={})", result.kcv);

        } catch (Exception e) {
            log.error("[JPOS-SRV] Echec du traitement de la PEK : {}", e.getMessage(), e);
        }
    }
}
