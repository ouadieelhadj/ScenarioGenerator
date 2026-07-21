package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.iso.McDmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import com.staging.sg.common.repository.McDmasKekRepository;
import com.staging.sg.common.repository.McDmasMemberKeyRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Echange de cles cote MEMBRE.
 *
 * ------------------------------------------------------------------
 *  DEUX MECANISMES COEXISTENT
 * ------------------------------------------------------------------
 *
 * 1. SOLLICITATION (162) — conforme aux traces du simulateur officiel :
 *
 *      membre -> reseau : 0800 DE70=162   sollicitation
 *      reseau -> membre : 0810 DE70=162   accuse, SANS cle
 *      reseau -> membre : 0800 DE70=161   la cle, DE48 SE11   [SPONTANE]
 *      membre -> reseau : 0810 DE70=161   accuse
 *      reseau -> membre : 0820 DE70=161   acquittement : cle utilisable
 *
 *    Flux ASYNCHRONE : la sollicitation n'obtient qu'un accuse, la cle
 *    arrive plus tard sur le thread listener. D'ou la machine a etats
 *    PENDING -> RECEIVED -> ACTIVE.
 *
 * 2. PUSH PAR LE MEMBRE (161 direct) — {@link #exchangePek} :
 *    NON CONFORME AUX SPECIFICATIONS. Voir la note sur la methode.
 *
 * Une troisieme voie, l'injection manuelle par REST, mene a la meme
 * table (voir McDmasKeyInjectionController).
 *
 * ------------------------------------------------------------------
 *  CRYPTOGRAPHIE (verifiee contre la trace du simulateur)
 * ------------------------------------------------------------------
 *      cle chiffree = 3DES-ECB(cle claire) sous KEK
 *      KCV          = 3DES-ECB(8 octets nuls) avec la cle claire
 *
 *      KEK   13AED5DA1F32347523C708C11F2608FD   KCV 2D617C
 *      PEK   BC4AEA2F5BB3FD1504624F8623835D5B   KCV 43A186
 */
@Service
public class McDmasKeyExchange {

    private static final Logger log = LoggerFactory.getLogger(McDmasKeyExchange.class);

    /** Codes DE70 de gestion de cles. */
    public static final String DE70_KEY_DELIVERY     = "161";
    public static final String DE70_KEY_SOLICITATION = "162";
    public static final String DE70_KEY_SUCCESS      = "164";
    public static final String DE70_KEY_FAILURE      = "165";

    private final McDmasNetworkUtil net;
    private final HsmService hsm;
    private final McDmasKekRepository kekRepo;
    private final McDmasMemberKeyRepository acqKeyRepo;
    private final McDmasMemberClient client;

    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;
    @Value("${dmas.member-group-id:TESTGRP01}") private String defaultMgid;

    private static final String FORWARDING_ID = "002202";

    public McDmasKeyExchange(McDmasNetworkUtil net, HsmService hsm,
                             McDmasKekRepository kekRepo,
                             McDmasMemberKeyRepository acqKeyRepo,
                             McDmasMemberClient client) {
        this.net = net;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.acqKeyRepo = acqKeyRepo;
        this.client = client;
    }

    // ====================================================================
    //  MECANISME 162 — SOLLICITATION
    // ====================================================================

    /**
     * Envoie 0800 DE70=162 et attend le 0810. La cle n'arrive PAS ici :
     * elle sera poussee ensuite par le reseau dans un 0800 DE70=161,
     * traite par {@link #handleKeyDelivery} depuis le thread listener.
     */
    public Map<String, Object> solicitPek(String mgid) throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();

        McDmasKek kek = kekRepo.findByMemberGroupId(mgid).orElse(null);
        if (kek == null || kek.getKekClear() == null) {
            r.put("success", false);
            r.put("error", "KEK absente pour " + mgid
                         + " — faire le bootstrap avant la sollicitation");
            return r;
        }

        String stan = net.generateStan();
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg req = new ISOMsg();
        req.setPackager(net.getPackager());
        req.setMTI("0800");
        req.set(2,  mgid);
        req.set(7,  dt);
        req.set(11, stan);
        // DE33 : c'est lui qui identifie le demandeur pour le reseau
        // (specifications p.154 : "the customer identified in DE 33").
        req.set(33, FORWARDING_ID);
        req.set(63, "BNET" + net.generateStan());
        req.set(70, DE70_KEY_SOLICITATION);

        log.info("[DMAS-KEX] Sollicitation d'echange de cle (0800 DE70=162 STAN={})", stan);

        ISOMsg resp = client.pushAndWait(req, timeoutSeconds);
        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);

        log.info("[DMAS-KEX] <- 0810 DE39={} — la cle arrivera dans un 0800/161", rc);

        r.put("step",         "SOLICITATION");
        r.put("mti_sent",     "0800");
        r.put("de070",        DE70_KEY_SOLICITATION);
        r.put("stan",         stan);
        r.put("mti_received", resp.getMTI());
        r.put("de039",        rc);
        r.put("success",      ok);
        r.put("note",         "La cle sera poussee par le reseau dans un 0800 DE70=161");
        return r;
    }

    /**
     * Traite le 0800 DE70=161 pousse par le reseau : decode le DE48
     * subelement 11, importe la cle sous le LMK local, verifie le KCV,
     * persiste en statut RECEIVED.
     *
     * Appele depuis le thread listener de {@link McDmasMemberClient}.
     *
     * @return le code DE39 a renvoyer dans le 0810
     */
    public String handleKeyDelivery(ISOMsg msg) {
        try {
            // La KEK est indexee sur NOTRE member_group_id (dmas.member-group-id),
            // pas sur le DE2 du message : celui-ci porte le Group Sign-on ID,
            // un identifiant RESEAU (ex. 40260) et non la cle de la base.
            String mgid = defaultMgid;
            String de48 = net.safeGet(msg, 48);
            if (de48 == null || de48.isBlank()) {
                log.error("[DMAS-KEX] 0800/161 sans DE48 — rejet");
                return "30";
            }

            KeyExchangeBlock keb = KeyExchangeBlock.parseDe48(de48);
            keb.logDetail("0800/161 recu (DE48)");

            if (keb.encryptedKeyHex == null || keb.encryptedKeyHex.isBlank()) {
                log.error("[DMAS-KEX] DE48 sans cle — rejet");
                return "30";
            }

            McDmasKek kek = kekRepo.findByMemberGroupId(mgid).orElse(null);
            if (kek == null || kek.getKekClear() == null) {
                log.error("[DMAS-KEX] KEK absente pour {} — import impossible", mgid);
                return "96";
            }

            String keyUnderKek = keb.encryptedKeyHex.trim();
            int keyLen = keyUnderKek.length() / 2;

            HsmService.KeyResult imp =
                    hsm.importWorkingKey("PEK", keyUnderKek, kek.getKekClear(), keyLen);

            // KCV recu sur 16 hex : comparaison sur les 6 premiers
            String kcvRecu = (keb.kcv != null && keb.kcv.length() >= 6)
                    ? keb.kcv.substring(0, 6) : keb.kcv;
            boolean kcvOk = imp.kcv != null && imp.kcv.equalsIgnoreCase(kcvRecu);

            log.info("[DMAS-KEX] PEK importee — KCV recu={} calcule={} match={}",
                    kcvRecu, imp.kcv, kcvOk);

            if (!kcvOk) {
                log.error("[DMAS-KEX] KCV different — cle rejetee");
                return "30";
            }

            // Statut RECEIVED : la cle n'est utilisable qu'apres le 0820
            McDmasMemberKey k = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, "PEK", "RECEIVED")
                    .orElseGet(McDmasMemberKey::new);
            k.setMemberGroupId(mgid);
            k.setKeyType("PEK");
            k.setKeyLength(keyLen);
            k.setKeyUnderLmk(imp.keyUnderLmkHex);
            k.setKeyUnderKek(keyUnderKek.length() > 64 ? keyUnderKek.substring(0, 64) : keyUnderKek);
            k.setKcv(imp.kcv);
            k.setStatus("RECEIVED");
            acqKeyRepo.save(k);

            log.info("[DMAS-KEX] PEK persistee en RECEIVED (KCV={}) — attente du 0820",
                    imp.kcv);
            return "00";

        } catch (Exception e) {
            log.error("[DMAS-KEX] Erreur de traitement de la cle : {}", e.getMessage(), e);
            return "96";
        }
    }

    /**
     * Traite le 0820 DE70=161 : le reseau confirme que la cle est
     * utilisable. Passe la cle de RECEIVED a ACTIVE et retire l'ancienne.
     */
    public void handleKeyAcknowledgement(ISOMsg msg) {
        try {
            String mgid = defaultMgid;   // cle locale, cf. handleKeyDelivery

            McDmasMemberKey recue = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, "PEK", "RECEIVED")
                    .orElse(null);
            if (recue == null) {
                log.warn("[DMAS-KEX] 0820 recu mais aucune PEK en attente d'activation");
                return;
            }

            acqKeyRepo.findByMemberGroupIdAndKeyTypeAndStatus(mgid, "PEK", "ACTIVE")
                    .ifPresent(old -> {
                        old.setStatus("RETIRED");
                        acqKeyRepo.save(old);
                        log.info("[DMAS-KEX] Ancienne PEK retiree (KCV={})", old.getKcv());
                    });

            recue.setStatus("ACTIVE");
            acqKeyRepo.save(recue);
            log.info("[DMAS-KEX] PEK ACTIVE (KCV={}) — utilisable pour le chiffrement PIN",
                    recue.getKcv());

        } catch (Exception e) {
            log.error("[DMAS-KEX] Erreur au traitement du 0820 : {}", e.getMessage(), e);
        }
    }

    // ====================================================================
    //  MECANISME NON CONFORME — le membre genere et pousse la cle
    // ====================================================================

    /**
     * Le membre genere lui-meme la PEK et la pousse au reseau.
     *
     * NON CONFORME AUX SPECIFICATIONS DMAS. Le guide decrit deux flux
     * (p.154 et p.157) et, dans les DEUX, c'est le RESEAU qui genere et
     * distribue la cle :
     *
     *   "customer generated"  le client SOLLICITE, le reseau livre
     *   "system generated"    le reseau livre spontanement, toutes les 24 h
     *
     * Le qualificatif "customer generated" porte sur la DEMANDE, pas sur
     * la cle — c'est ce qui avait induit en erreur.
     *
     * Conserve car utile en test : il exerce le chemin de reception du
     * handler cote reseau (importKeyFromDe48). A ne pas utiliser contre
     * un vrai MIP.
     *
     * @deprecated utiliser {@link #solicitPek} (mecanisme 162).
     */
    @Deprecated
    public Map<String, Object> exchangePek(String memberGroupId) throws Exception {
        return exchange(memberGroupId, "PEK", DE70_KEY_DELIVERY);
    }

    private Map<String, Object> exchange(String mgid, String keyType, String de070)
            throws Exception {
        McDmasKek kek = kekRepo.findByMemberGroupId(mgid)
                .orElseThrow(() -> new IllegalStateException("KEK introuvable pour " + mgid));
        if (kek.getKekClear() == null) {
            throw new IllegalStateException("kek_clear absent pour " + mgid);
        }

        int keyLen = (kek.getKeyLength() != null) ? kek.getKeyLength() : 24;

        HsmService.KeyResult gen = hsm.generateWorkingKey(keyType, keyLen, kek.getKekClear());

        KeyExchangeBlock keb = new KeyExchangeBlock();
        keb.keyClassId      = KeyExchangeBlock.KEY_CLASS_PIN;
        keb.keyIndex        = "00";
        keb.keyCycle        = "00";
        keb.encryptedKeyHex = gen.keyUnderKekHex;
        keb.kcv             = gen.kcv;
        String de048 = keb.buildDe48();

        String banknetRef = "BNET" + net.generateStan();
        String stan = net.generateStan();
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

        ISOMsg req = new ISOMsg();
        req.setPackager(net.getPackager());
        req.setMTI("0800");
        req.set(2,  mgid);
        req.set(7,  dt);
        req.set(11, stan);
        req.set(33, FORWARDING_ID);
        req.set(48, de048);
        req.set(63, banknetRef);
        req.set(70, de070);

        log.info("[DMAS-KEX] === 0800 push de cle (DE70={}) ===", de070);
        log.info("[DMAS-KEX] DE2  Member Group ID       = {}", mgid);
        log.info("[DMAS-KEX] DE7  Transmission DateTime = {}", dt);
        log.info("[DMAS-KEX] DE11 STAN                  = {}", stan);
        log.info("[DMAS-KEX] DE33 Forwarding Inst ID    = {}", FORWARDING_ID);
        log.info("[DMAS-KEX] DE63 Network Data          = {}", banknetRef);
        keb.logDetail("0800 envoye (DE48)");

        String reqHex = ISOUtil.hexString(req.pack());
        ISOMsg resp = client.pushAndWait(req, timeoutSeconds);
        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);

        log.info("[DMAS-KEX] <- 0810 DE39={} ok={}", rc, ok);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key_type", keyType);
        r.put("member_group_id", mgid);
        r.put("de070", de070);
        r.put("stan", stan);
        r.put("kcv_sent", gen.kcv);
        r.put("de048_sent", de048);
        r.put("de063_sent", banknetRef);
        r.put("thales_a0", gen.thalesCommand);
        r.put("de039", rc);
        r.put("success", ok);
        r.put("request_hex", reqHex);
        r.put("response_hex", ISOUtil.hexString(resp.pack()));

        if (ok) {
            McDmasMemberKey ak = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, keyType, "ACTIVE")
                    .orElseGet(McDmasMemberKey::new);
            ak.setMemberGroupId(mgid);
            ak.setKeyType(keyType);
            ak.setKeyLength(keyLen);
            ak.setKeyUnderLmk(gen.keyUnderLmkHex);
            ak.setKeyUnderKek(gen.keyUnderKekHex.length() > 64
                    ? gen.keyUnderKekHex.substring(0, 64) : gen.keyUnderKekHex);
            ak.setKcv(gen.kcv);
            ak.setStatus("ACTIVE");
            acqKeyRepo.save(ak);
            log.info("[DMAS-KEX] {} persiste dans mc_dmas_member_keys (KCV={})",
                    keyType, gen.kcv);
        }

        // 0820 : confirmation de succes (164) ou avis d'echec (165)
        try {
            String adviceDe70 = ok ? DE70_KEY_SUCCESS : DE70_KEY_FAILURE;
            ISOMsg advice = new ISOMsg();
            advice.setPackager(net.getPackager());
            advice.setMTI("0820");
            advice.set(2,  mgid);
            advice.set(7,  new SimpleDateFormat("MMddHHmmss").format(new Date()));
            advice.set(11, net.generateStan());
            advice.set(33, FORWARDING_ID);
            advice.set(63, banknetRef);
            advice.set(70, adviceDe70);

            client.pushOnActiveSession(advice);
            log.info("[DMAS-KEX] 0820 advice envoye : DE70={} (ok={})", adviceDe70, ok);
            r.put("de070_advice", adviceDe70);
            r.put("advice_sent", true);
        } catch (Exception e) {
            log.error("[DMAS-KEX] Echec de l'envoi du 0820 : {}", e.getMessage(), e);
            r.put("advice_sent", false);
        }

        return r;
    }
}
