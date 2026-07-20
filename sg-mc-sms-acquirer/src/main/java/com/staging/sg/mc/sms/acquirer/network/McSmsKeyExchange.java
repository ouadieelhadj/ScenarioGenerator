package com.staging.sg.mc.sms.acquirer.network;

import com.staging.sg.common.iso.McSmsDe48;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.mc.sms.acquirer.entity.McSmsAcqKey;
import com.staging.sg.mc.sms.acquirer.entity.McSmsKek;
import com.staging.sg.mc.sms.acquirer.repository.McSmsAcqKeyRepository;
import com.staging.sg.mc.sms.acquirer.repository.McSmsKekRepository;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Echange de cles cote MEMBRE (Mastercard SMS acquereur) — mecanisme 162.
 *
 * ------------------------------------------------------------------
 *  FLUX (verifie contre une trace du simulateur Mastercard officiel)
 * ------------------------------------------------------------------
 *   1. Membre -> MIP : 0800 DE70=162   Solicitation for Encryption Key Exchange
 *   2. MIP -> Membre : 0810 DE70=162   DE39=00, PAS de cle
 *   3. MIP -> Membre : 0800 DE70=161   la cle, dans DE48 subelement 11  [SPONTANE]
 *   4. Membre -> MIP : 0810 DE70=161   accuse (DE39=00 si import OK, 96 sinon)
 *   5. MIP -> Membre : 0820 DE70=161   acquittement : la cle est utilisable
 *
 * DIFFERENCE MAJEURE AVEC SWAM : le flux est ASYNCHRONE. Chez SWAM la cle
 * arrive dans la reponse au 1804 (un seul aller-retour, tout dans une methode).
 * Ici la demande n'obtient qu'un accuse ; la cle arrive PLUS TARD, sur le
 * thread receiver, dans un 0800 spontane. D'ou la machine a etats ci-dessous.
 *
 * ------------------------------------------------------------------
 *  CRYPTOGRAPHIE (verifiee contre la trace)
 * ------------------------------------------------------------------
 *   cle chiffree = 3DES-ECB(cle claire) sous ZMK
 *   KCV          = 3DES-ECB(8 octets nuls) avec la cle claire, tronque
 *
 *   ZMK    13AED5DA1F32347523C708C11F2608FD  (KCV 2D617C)
 *   clair  BC4AEA2F5BB3FD1504624F8623835D5B
 *   -> SF4 E02B0E8BD4644E6341182D71F4F3F5B5
 *   -> KCV 43A1866D253E9365, tronque a "43A1" dans SF5
 *
 * ATTENTION : Mastercard tronque le KCV a 4 caracteres, la ou SWAM en
 * conserve 6. La comparaison se fait donc sur les 4 premiers.
 *
 * Le mecanisme 163 (TR-31 keyblock, transport par DE110) n'est PAS
 * implemente ici — voir SESSION_RESUME pour la specification complete.
 */
@Service
public class McSmsKeyExchange {

    private static final Logger log = LoggerFactory.getLogger(McSmsKeyExchange.class);

    /** Nombre de caracteres significatifs du KCV chez Mastercard. */
    private static final int KCV_SIGNIFICANT_CHARS = 4;

    /** Delai d'attente de la cle apres la sollicitation (secondes). */
    private static final int KEY_WAIT_SECONDS = 30;

    private final McSmsJposClient client;
    private final JposHsmService hsm;
    private final McSmsKekRepository kekRepo;
    private final McSmsAcqKeyRepository acqKeyRepo;

    @Value("${mc.sms.member-group-id:MCTESTGRP}")
    private String memberGroupId;

    public McSmsKeyExchange(McSmsJposClient client,
                            JposHsmService hsm,
                            McSmsKekRepository kekRepo,
                            McSmsAcqKeyRepository acqKeyRepo) {
        this.client = client;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.acqKeyRepo = acqKeyRepo;
    }

    // ====================================================================
    //  ETAPE 1-2 : SOLLICITATION
    // ====================================================================

    /**
     * Envoie 0800 DE70=162 et attend le 0810. La cle n'arrive PAS ici :
     * elle sera poussee ensuite par le MIP dans un 0800 DE70=161, traite
     * par {@link #handleKeyDelivery(ISOMsg)} depuis le thread receiver.
     */
    public Map<String, Object> solicitKeyExchange() throws Exception {
        Map<String, Object> r = new LinkedHashMap<>();

        McSmsKek kek = kekRepo.findByMemberGroupId(memberGroupId).orElse(null);
        if (kek == null || kek.getKekClear() == null) {
            r.put("success", false);
            r.put("error", "ZMK absente pour " + memberGroupId
                         + " — faire le bootstrap avant la sollicitation");
            return r;
        }

        client.connect();
        ISOMsg req = client.buildNetworkRequest(McSmsJposClient.DE70_KEY_SOLICITATION);
        log.info("[MC-KEX] Sollicitation d'echange de cle (0800 DE70=162)");

        ISOMsg resp = client.sendAndWait(req, KEY_WAIT_SECONDS);
        String de39 = resp.hasField(39) ? resp.getString(39) : null;

        r.put("step",          "SOLICITATION");
        r.put("mti_sent",      "0800");
        r.put("de70",          McSmsJposClient.DE70_KEY_SOLICITATION);
        r.put("stan",          req.getString(11));
        r.put("mti_received",  resp.getMTI());
        r.put("de39",          de39);
        r.put("success",       "00".equals(de39));
        r.put("note",          "La cle arrivera dans un 0800 DE70=161 pousse par le MIP");
        return r;
    }

    // ====================================================================
    //  ETAPE 3-4 : RECEPTION DE LA CLE
    // ====================================================================

    /**
     * Traite le 0800 DE70=161 pousse par le MIP : decode le DE48 subelement 11,
     * importe la cle sous le LMK local, verifie le KCV, persiste.
     *
     * Appele depuis le thread receiver de {@link McSmsJposClient}.
     *
     * @return le code DE39 a renvoyer dans le 0810 : "00" si tout est bon,
     *         "96" en cas d'erreur systeme, "30" si le message est mal forme.
     */
    public String handleKeyDelivery(ISOMsg msg) {
        try {
            String de48 = msg.hasField(48) ? msg.getString(48) : null;
            if (de48 == null) {
                log.error("[MC-KEX] 0800/161 sans DE48 — rejet");
                return "30";
            }

            McSmsDe48.KeyExchangeBlock blk =
                    McSmsDe48.parse(de48).keyExchangeBlock();

            if (blk == null) {
                log.error("[MC-KEX] Subelement 11 absent ou illisible — rejet");
                return "30";
            }
            log.info("[MC-KEX] Cle recue : {}", blk);

            if (blk.isAcknowledgement()) {
                log.warn("[MC-KEX] Subelement 11 sans cle dans un 0800/161 — ignore");
                return "30";
            }

            McSmsKek kek = kekRepo.findByMemberGroupId(memberGroupId).orElse(null);
            if (kek == null || kek.getKekClear() == null) {
                log.error("[MC-KEX] ZMK absente — impossible d'importer");
                return "96";
            }

            String encryptedKey = blk.encryptedKey.trim();
            int keyLenBytes = blk.keyLengthBytes();

            // Import sous LMK local. La longueur vient de la CLE RECUE,
            // pas d'une constante — meme lecon que SWAM (cf. session 17).
            HsmService.KeyResult imp = (keyLenBytes >= 16)
                    ? hsm.importWorkingKey("PEK", encryptedKey, kek.getKekClear(), keyLenBytes)
                    : hsm.importWorkingKeySingle("PEK", encryptedKey, kek.getKekClear());

            // Mastercard ne transmet que 4 caracteres de KCV.
            String kcvReceived = blk.kcvTrimmed();
            String kcvComputed = imp.kcv == null ? "" : imp.kcv;
            boolean kcvOk = kcvComputed.regionMatches(true, 0, kcvReceived, 0,
                    Math.min(KCV_SIGNIFICANT_CHARS, kcvReceived.length()));

            if (!kcvOk) {
                log.error("[MC-KEX] KCV different : recu={} calcule={} — rejet",
                        kcvReceived, kcvComputed);
                return "96";
            }

            // Persistance : la cle est RECEIVED tant que le 0820 n'est pas arrive.
            McSmsAcqKey key = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "PEK", "ACTIVE")
                    .orElseGet(McSmsAcqKey::new);
            key.setMemberGroupId(memberGroupId);
            key.setKeyType("PEK");
            key.setKeyLength(keyLenBytes);
            key.setKeyUnderLmk(imp.keyUnderLmkHex);
            key.setKeyUnderKek(encryptedKey);
            key.setKcv(imp.kcv == null ? null
                    : imp.kcv.substring(0, Math.min(6, imp.kcv.length())));
            key.setStatus("RECEIVED");
            acqKeyRepo.save(key);

            log.info("[MC-KEX] PEK importee et persistee (KCV={}, {} octets) — "
                   + "en attente du 0820 pour activation", imp.kcv, keyLenBytes);
            return "00";

        } catch (Exception e) {
            log.error("[MC-KEX] Erreur de traitement de la cle : {}", e.getMessage(), e);
            return "96";
        }
    }

    // ====================================================================
    //  ETAPE 5 : ACQUITTEMENT
    // ====================================================================

    /**
     * Traite le 0820 DE70=161 : le MIP confirme que la cle est utilisable.
     * Passe la cle de RECEIVED a ACTIVE.
     *
     * Guide p.36090 : "Upon receipt of a Network Management Advice/0820
     * message, processors may begin to use the new working key delivered in
     * the Network Management Request/0800 message."
     */
    public void handleKeyAcknowledgement(ISOMsg msg) {
        try {
            McSmsAcqKey key = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "PEK", "RECEIVED")
                    .orElse(null);

            if (key == null) {
                log.warn("[MC-KEX] 0820 recu mais aucune PEK en attente d'activation");
                return;
            }

            // Retirer le statut ACTIVE de l'ancienne cle, s'il y en a une
            acqKeyRepo.findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "PEK", "ACTIVE")
                    .ifPresent(old -> {
                        old.setStatus("RETIRED");
                        acqKeyRepo.save(old);
                        log.info("[MC-KEX] Ancienne PEK retiree (KCV={})", old.getKcv());
                    });

            key.setStatus("ACTIVE");
            acqKeyRepo.save(key);
            log.info("[MC-KEX] PEK ACTIVE (KCV={}) — utilisable pour le chiffrement PIN",
                    key.getKcv());

        } catch (Exception e) {
            log.error("[MC-KEX] Erreur au traitement du 0820 : {}", e.getMessage(), e);
        }
    }

    // ====================================================================
    //  CONSULTATION
    // ====================================================================

    /** Etat courant des cles du membre. */
    public Map<String, Object> currentKey() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("member_group_id", memberGroupId);

        acqKeyRepo.findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "PEK", "ACTIVE")
                .ifPresentOrElse(k -> {
                    r.put("status", "ACTIVE");
                    r.put("kcv", k.getKcv());
                    r.put("key_length_bytes", k.getKeyLength());
                }, () -> acqKeyRepo
                        .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "PEK", "RECEIVED")
                        .ifPresentOrElse(k -> {
                            r.put("status", "RECEIVED");
                            r.put("kcv", k.getKcv());
                            r.put("note", "en attente du 0820 pour activation");
                        }, () -> r.put("status", "NONE")));

        kekRepo.findByMemberGroupId(memberGroupId).ifPresent(k -> {
            r.put("zmk_present", k.getKekClear() != null);
            r.put("zmk_kcv", k.getKcv());
        });
        return r;
    }
}
