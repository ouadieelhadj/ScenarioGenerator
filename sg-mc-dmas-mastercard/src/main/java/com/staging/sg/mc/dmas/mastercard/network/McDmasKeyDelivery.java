package com.staging.sg.mc.dmas.mastercard.network;

import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMastercardKey;
import com.staging.sg.common.iso.McDmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import com.staging.sg.common.repository.McDmasKekRepository;
import com.staging.sg.common.repository.McDmasMastercardKeyRepository;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Livraison de cle cote RESEAU MASTERCARD simule — mecanisme 162.
 *
 * DEUX FLUX, tous deux prevus par les specifications DMAS (p.154 et 157) :
 *
 *   CUSTOMER GENERATED  le client sollicite (0800/162), le reseau livre
 *   SYSTEM GENERATED    le reseau livre spontanement, toutes les 24 heures
 *
 * ATTENTION AU VOCABULAIRE : "customer generated" qualifie la DEMANDE,
 * pas la cle. Dans les deux cas c'est le RESEAU qui genere et distribue
 * la PEK ; le client ne fait que la recevoir et l'accuser.
 *
 * Reproduit le comportement observe dans la trace du simulateur officiel :
 *
 *   1. recoit  0800 DE70=162  sollicitation du membre
 *   2. envoie  0810 DE70=162  DE39=00, sans cle        (fait par le handler)
 *   3. genere une PEK, la chiffre sous la KEK
 *   4. envoie  0800 DE70=161  avec DE48 subelement 11  [SPONTANE]
 *   5. recoit  0810 DE70=161  accuse du membre
 *   6. envoie  0820 DE70=161  acquittement : la cle devient utilisable
 *
 * Le 0820 est emis MEME SI le membre a repondu en erreur — comportement
 * observe dans la trace (DE39=96 suivi malgre tout du 0820).
 *
 * La livraison est asynchrone : elle part dans un thread separe pour ne
 * pas bloquer le listener jPOS qui doit d'abord emettre le 0810.
 */
@Service
public class McDmasKeyDelivery {

    private static final Logger log = LoggerFactory.getLogger(McDmasKeyDelivery.class);

    /** Delai avant la livraison, pour laisser passer le 0810 de la sollicitation. */
    private static final long DELAY_BEFORE_KEY_MS = 300;

    /** Delai avant l'acquittement 0820, apres le 0810 du membre. */
    private static final long DELAY_BEFORE_ACK_MS = 400;

    private static final String FORWARDING_ID = "011901";

    private final McDmasNetworkUtil net;
    private final HsmService hsm;
    private final McDmasKekRepository kekRepo;
    private final McDmasMastercardKeyRepository issKeyRepo;
    private final McDmasMastercardServer server;

    @Value("${dmas.member-group:TESTGRP01}")
    private String defaultMgid;

    @Value("${dmas.timeout-seconds:30}")
    private int timeoutSeconds;

    /** Renouvellement automatique toutes les 24 h (flux system generated). */
    @Value("${dmas.pek.auto-renewal:false}")
    private boolean autoRenewal;

    /** Derniere cle livree, conservee pour le controle et les tests. */
    private volatile String lastKcv;
    private volatile String lastKeyUnderKek;

    public McDmasKeyDelivery(McDmasNetworkUtil net, HsmService hsm,
                             McDmasKekRepository kekRepo,
                             McDmasMastercardKeyRepository issKeyRepo,
                             @Lazy McDmasMastercardServer server) {
        this.net = net;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.issKeyRepo = issKeyRepo;
        this.server = server;
    }

    public String getLastKcv()         { return lastKcv; }
    public String getLastKeyUnderKek() { return lastKeyUnderKek; }

    // ====================================================================
    //  ORCHESTRATION
    // ====================================================================

    /**
     * Lance la livraison apres que le 0810 de la sollicitation a ete emis.
     * Execute dans un thread separe pour ne pas bloquer le listener.
     */
    public void deliverAsync(ISOMsg solicitation) {
        String mgid = solicitation.hasField(2) ? solicitation.getString(2) : defaultMgid;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(DELAY_BEFORE_KEY_MS);
                deliver(mgid, solicitation);
            } catch (Exception e) {
                log.error("[DMAS-KEXS] Echec de la livraison : {}", e.getMessage(), e);
            }
        }, "mc-dmas-key-delivery");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Livraison spontanee, sans sollicitation prealable — flux
     * "system generated". Utilisee par le renouvellement periodique et
     * par l'endpoint de declenchement manuel.
     */
    public void deliverSpontaneous(String mgid) {
        Thread t = new Thread(() -> {
            try {
                deliver(mgid, null);
            } catch (Exception e) {
                log.error("[DMAS-KEXS] Echec de la livraison spontanee : {}", e.getMessage(), e);
            }
        }, "mc-dmas-key-delivery-auto");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Renouvellement periodique de la PEK — flux "system generated".
     *
     * Les specifications prevoient un echange toutes les 24 heures
     * (p.157). Desactive par defaut pour ne pas perturber les tests :
     * mettre dmas.pek.auto-renewal=true pour l'activer.
     */
    @Scheduled(fixedRateString = "${dmas.pek.renewal-interval-ms:86400000}",
               initialDelayString = "${dmas.pek.renewal-initial-delay-ms:86400000}")
    public void scheduledRenewal() {
        if (!autoRenewal) return;
        if (!server.hasActiveSession()) {
            log.debug("[DMAS-KEXS] Renouvellement ignore : aucune session membre");
            return;
        }
        // ATTENTION : getActiveMemberGroupId() retourne le DE2 du sign-on
        // (Group Sign-on ID, ex. 40260), qui identifie le membre sur le
        // RESEAU. La KEK, elle, est indexee sur le member_group_id INTERNE
        // (ex. TESTGRP01). Deux notions distinctes aux noms proches.
        log.info("[DMAS-KEXS] Renouvellement periodique de la PEK (system generated)");
        deliverSpontaneous(defaultMgid);
    }

    /** Genere, chiffre et pousse la cle dans un 0800 DE70=161. */
    private void deliver(String mgid, ISOMsg solicitation) throws Exception {
        McDmasKek kek = kekRepo.findByMemberGroupId(mgid).orElse(null);
        if (kek == null || kek.getKekClear() == null) {
            log.error("[DMAS-KEXS] KEK absente pour {} — livraison impossible", mgid);
            return;
        }

        int keyLen = (kek.getKeyLength() != null) ? kek.getKeyLength() : 16;

        // Generation de la PEK sous LMK local + chiffrement sous KEK
        HsmService.KeyResult gen = hsm.generateWorkingKey("PEK", keyLen, kek.getKekClear());
        lastKcv = gen.kcv;
        lastKeyUnderKek = gen.keyUnderKekHex;

        log.info("[DMAS-KEXS] PEK generee — KCV={} ({} octets)", gen.kcv, keyLen);

        KeyExchangeBlock keb = new KeyExchangeBlock();
        keb.keyClassId      = KeyExchangeBlock.KEY_CLASS_PIN;
        keb.keyIndex        = "00";
        keb.keyCycle        = "00";
        keb.encryptedKeyHex = gen.keyUnderKekHex;
        keb.kcv             = gen.kcv;
        String de48 = keb.buildDe48();
        keb.logDetail("0800/161 envoye (DE48)");

        // DE2 : identifiant du membre SUR LE RESEAU (Group Sign-on ID),
        // distinct de mgid qui indexe les cles en base.
        String de2 = server.getActiveMemberGroupId();
        if (de2 == null || de2.isBlank()) de2 = mgid;

        String stan = net.generateStan();
        ISOMsg m = new ISOMsg();
        m.setPackager(net.getPackager());
        m.setMTI("0800");
        m.set(2,  de2);
        m.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
        m.set(11, stan);
        m.set(33, FORWARDING_ID);
        m.set(48, de48);
        if (solicitation != null && solicitation.hasField(63)) {
            m.set(63, solicitation.getString(63));
        } else {
            m.set(63, "BNET" + net.generateStan());
        }
        m.set(70, "161");

        log.info("[DMAS-KEXS] Livraison de la cle ({}) : 0800 DE70=161 STAN={}",
                solicitation != null ? "sur sollicitation" : "spontanee", stan);
        ISOMsg resp = server.pushAndWait(m, timeoutSeconds);

        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);
        log.info("[DMAS-KEXS] <- 0810 DE39={} — le membre {} la cle",
                rc, ok ? "a accepte" : "a rejete");

        // Persistance cote reseau : la cle est desormais la notre aussi
        if (ok) {
            McDmasMastercardKey ik = issKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, "PEK", "ACTIVE")
                    .orElseGet(McDmasMastercardKey::new);
            ik.setMemberGroupId(mgid);
            ik.setKeyType("PEK");
            ik.setKeyLength(keyLen);
            ik.setKeyUnderLmk(gen.keyUnderLmkHex);
            ik.setKeyUnderKek(gen.keyUnderKekHex.length() > 64
                    ? gen.keyUnderKekHex.substring(0, 64) : gen.keyUnderKekHex);
            ik.setKcv(gen.kcv);
            ik.setStatus("ACTIVE");
            issKeyRepo.save(ik);
            log.info("[DMAS-KEXS] PEK persistee dans mc_dmas_mastercard_keys (KCV={})", gen.kcv);
        }

        // Acquittement 0820, emis meme en cas de rejet (cf. trace)
        sendAcknowledgement(mgid, m, ok);
    }

    /** Envoie le 0820 DE70=161 : la cle devient utilisable cote membre. */
    private void sendAcknowledgement(String mgid, ISOMsg delivery, boolean ok) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(DELAY_BEFORE_ACK_MS);

                String de2 = server.getActiveMemberGroupId();
                if (de2 == null || de2.isBlank()) de2 = mgid;

                ISOMsg m = new ISOMsg();
                m.setPackager(net.getPackager());
                m.setMTI("0820");
                m.set(2,  de2);
                m.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
                m.set(11, delivery.getString(11));   // meme STAN que la livraison
                m.set(33, FORWARDING_ID);
                if (delivery.hasField(63)) m.set(63, delivery.getString(63));
                m.set(70, "161");

                server.pushOnActiveSession(m);
                log.info("[DMAS-KEXS] Acquittement envoye : 0820 DE70=161 STAN={} (membre ok={})",
                        m.getString(11), ok);

            } catch (Exception e) {
                log.error("[DMAS-KEXS] Echec de l'acquittement : {}", e.getMessage(), e);
            }
        }, "mc-dmas-key-ack");
        t.setDaemon(true);
        t.start();
    }
}
