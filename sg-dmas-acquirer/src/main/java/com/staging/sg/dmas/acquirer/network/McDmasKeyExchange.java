package com.staging.sg.dmas.acquirer.network;

import com.staging.sg.common.entity.DmasKek;
import com.staging.sg.common.iso.DmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.DmasKekRepository;
import com.staging.sg.common.repository.DmasAcqKeyRepository;
import com.staging.sg.common.entity.DmasAcqKey;
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
 * Échange de clés PEK/MAK côté ACQUEREUR.
 *
 * Pour chaque clé :
 *  1. lit kek_under_acq_lmk depuis dmas_kek
 *  2. generateWorkingKey -> clé chiffrée sous KEK + KCV
 *  3. construit DE048 sub11 = [Type 2c][Len 2c][clé hex][KCV 6hex]
 *  4. envoie 0800 DE070=101 (PEK) / 102 (MAK)
 *  5. lit 0810 DE39 (00 = OK)
 */
@Service
public class McDmasKeyExchange {

    private static final Logger log = LoggerFactory.getLogger(McDmasKeyExchange.class);

    private final DmasNetworkUtil net;
    private final HsmService hsm;
    private final DmasKekRepository kekRepo;
    private final DmasAcqKeyRepository acqKeyRepo;

    @Value("${dmas.issuer-host:localhost}") private String issuerHost;
    @Value("${dmas.issuer-port:8500}")      private int    issuerPort;
    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;

    public McDmasKeyExchange(DmasNetworkUtil net, HsmService hsm, DmasKekRepository kekRepo, DmasAcqKeyRepository acqKeyRepo) {
        this.net = net;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.acqKeyRepo = acqKeyRepo;
    }

    public Map<String,Object> exchangePek(String memberGroupId) throws Exception {
        return exchange(memberGroupId, "PEK", "PE", "101");
    }

    public Map<String,Object> exchangeMak(String memberGroupId) throws Exception {
        return exchange(memberGroupId, "MAK", "MA", "102");
    }

    private Map<String,Object> exchange(String mgid, String keyType, String typeCode, String de070) throws Exception {
        DmasKek kek = kekRepo.findByMemberGroupId(mgid)
                .orElseThrow(() -> new IllegalStateException("KEK introuvable pour " + mgid));
        if (kek.getKekClear() == null)
            throw new IllegalStateException("kek_clear absent pour " + mgid);

        int keyLen = (kek.getKeyLength() != null) ? kek.getKeyLength() : 24;

        // 1+2. Générer la clé de travail chiffrée sous KEK (valeur claire du KEK)
        HsmService.KeyResult gen = hsm.generateWorkingKey(keyType, keyLen, kek.getKekClear());

        // 3. Construire le DE048 : [Type 2c][Len 2c][clé hex][KCV 6hex]
        String lenField = String.format("%02d", keyLen);
        String de048 = typeCode + lenField + gen.keyUnderKekHex + gen.kcv;

        // 4. Envoyer 0800
        String stan = net.generateStan();
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());
        ISOMsg req = new ISOMsg();
        req.setPackager(net.getPackager());
        req.setMTI("0800");
        req.set(7,  dt);
        req.set(11, stan);
        req.set(48, de048);
        req.set(70, de070);

        String reqHex = ISOUtil.hexString(req.pack());
        log.info("[DMAS-ACQ] Key exchange {} -> 0800 DE70={} KCV={} de48len={}",
                keyType, de070, gen.kcv, de048.length());

        ISOMsg resp = net.sendAndReceive(req, issuerHost, issuerPort, timeoutSeconds);
        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("key_type", keyType);
        r.put("member_group_id", mgid);
        r.put("de070", de070);
        r.put("stan", stan);
        r.put("kcv_sent", gen.kcv);
        r.put("de048_sent", de048);
        r.put("thales_a0", gen.thalesCommand);
        r.put("de039", rc);
        r.put("success", ok);
        r.put("request_hex", reqHex);
        r.put("response_hex", ISOUtil.hexString(resp.pack()));
        log.info("[DMAS-ACQ] Key exchange {} <- 0810 DE39={} ok={}", keyType, rc, ok);

        // Persister la clé sous LMK acquéreur si l'échange a réussi
        if (ok) {
            DmasAcqKey ak = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, keyType, "ACTIVE")
                    .orElseGet(DmasAcqKey::new);
            ak.setMemberGroupId(mgid);
            ak.setKeyType(keyType);
            ak.setKeyLength(keyLen);
            ak.setKeyUnderLmk(gen.keyUnderLmkHex);
            ak.setKeyUnderKek(gen.keyUnderKekHex.length() > 64 ? gen.keyUnderKekHex.substring(0,64) : gen.keyUnderKekHex);
            ak.setKcv(gen.kcv);
            ak.setStatus("ACTIVE");
            acqKeyRepo.save(ak);
            log.info("[DMAS-ACQ] {} persistée dans dmas_acq_keys (KCV={})", keyType, gen.kcv);
        }
        return r;
    }
}
