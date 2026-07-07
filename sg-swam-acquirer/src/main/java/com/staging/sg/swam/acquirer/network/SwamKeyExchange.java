package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.SwamDe48;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.SwamAcqKeyRepository;
import com.staging.sg.common.repository.SwamKekRepository;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Echange de cles cote MEMBRE/BANQUE (SWAM acquereur).
 * Conforme spec HPS : le membre DEMANDE (1804 DE24=811/899, sans cle), le
 * CENTRE genere et renvoie la cle dans le 1814 (DE48 P16/P10 + KCV K16/K10).
 * Le membre IMPORTE sous son LMK local et persiste dans swam_acq_keys.
 */
@Service
public class SwamKeyExchange {

    private static final Logger log = LoggerFactory.getLogger(SwamKeyExchange.class);
    private static final String MGID = "TESTGRP01";

    private final SwamJposClient client;
    private final SwamAuthorization auth;
    private final JposHsmService hsm;
    private final SwamKekRepository kekRepo;
    private final SwamAcqKeyRepository acqKeyRepo;

    public SwamKeyExchange(SwamJposClient client, SwamAuthorization auth, JposHsmService hsm,
                           SwamKekRepository kekRepo, SwamAcqKeyRepository acqKeyRepo) {
        this.client = client;
        this.auth = auth;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.acqKeyRepo = acqKeyRepo;
    }

    public Map<String,Object> exchangeZpk() throws Exception { return exchange("811", "PEK"); }
    public Map<String,Object> exchangeZak() throws Exception { return exchange("899", "MAK"); }

    private Map<String,Object> exchange(String func, String keyType) throws Exception {
        String tagKey = "811".equals(func) ? SwamDe48.TAG_ZPK : SwamDe48.TAG_ZAK;
        String tagKcv = "811".equals(func) ? SwamDe48.TAG_ZPK_KCV : SwamDe48.TAG_ZAK_KCV;

        SwamKek kek = kekRepo.findByMemberGroupId(MGID)
                .orElseThrow(() -> new IllegalStateException("KEK SWAM introuvable (bootstrap acquereur d'abord)"));
        if (kek.getKekClear() == null)
            throw new IllegalStateException("kek_clear absent : bootstrap acquereur d'abord");

        client.connect();
        // Demande 1804 DE24=811/899, SANS cle
        ISOMsg req = auth.buildNetwork(func, client.getPackager());
        ISOMsg resp = client.sendAndWait(req, 10);

        String de39 = resp.hasField(39) ? resp.getString(39) : null;
        String de48 = resp.hasField(48) ? resp.getString(48) : null;

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("key_type", keyType);
        r.put("de24_func", func);
        r.put("stan", req.getString(11));
        r.put("de39", de39);
        r.put("de48_received", de48);

        if (!"800".equals(de39) || de48 == null) {
            r.put("success", false);
            r.put("error", "reponse 1814 invalide (DE39=" + de39 + ", DE48 " + (de48 == null ? "absent" : "present") + ")");
            return r;
        }

        SwamDe48 parsed = SwamDe48.parse(de48);
        String keyUnderKekHex = parsed.get(tagKey);
        String kcvReceived = parsed.get(tagKcv);
        if (keyUnderKekHex == null) {
            r.put("success", false);
            r.put("error", "tag " + tagKey + " absent du DE48");
            return r;
        }
        int keyLen = keyUnderKekHex.length() / 2;

        // Import sous LMK local (simple longueur pour ZAK, double pour ZPK)
        HsmService.KeyResult imp = "811".equals(func)
                ? hsm.importWorkingKey(keyType, keyUnderKekHex, kek.getKekClear(), keyLen)
                : hsm.importWorkingKeySingle(keyType, keyUnderKekHex, kek.getKekClear());

        boolean kcvOk = imp.kcv.equalsIgnoreCase(kcvReceived);
        r.put("kcv_received", kcvReceived);
        r.put("kcv_computed", imp.kcv);
        r.put("kcv_match", kcvOk);

        if (kcvOk) {
            SwamAcqKey ak = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(MGID, keyType, "ACTIVE")
                    .orElseGet(SwamAcqKey::new);
            ak.setMemberGroupId(MGID);
            ak.setKeyType(keyType);
            ak.setKeyLength(keyLen);
            ak.setKeyUnderLmk(imp.keyUnderLmkHex);
            ak.setKeyUnderKek(keyUnderKekHex.length() > 64 ? keyUnderKekHex.substring(0,64) : keyUnderKekHex);
            ak.setKcv(imp.kcv);
            ak.setStatus("ACTIVE");
            acqKeyRepo.save(ak);
            log.info("[SWAM-ACQ] {} importe+persiste (KCV={})", keyType, imp.kcv);
            r.put("success", true);
        } else {
            log.warn("[SWAM-ACQ] {} KCV mismatch recu={} calcule={}", keyType, kcvReceived, imp.kcv);
            r.put("success", false);
            r.put("error", "KCV mismatch");
        }
        return r;
    }
}
