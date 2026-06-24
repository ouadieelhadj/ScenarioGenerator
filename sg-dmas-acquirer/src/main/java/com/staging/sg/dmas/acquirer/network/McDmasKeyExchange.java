package com.staging.sg.dmas.acquirer.network;

import com.staging.sg.common.entity.DmasKek;
import com.staging.sg.common.iso.DmasNetworkUtil;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.DmasKekRepository;
import com.staging.sg.common.repository.DmasAcqKeyRepository;
import com.staging.sg.common.entity.DmasAcqKey;
import com.staging.sg.common.iso.crypto.KeyExchangeBlock;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.staging.sg.dmas.acquirer.network.DmasJposServer;

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
    private final DmasJposServer dmasJposServer;

    @Value("${dmas.issuer-host:localhost}") private String issuerHost;
    @Value("${dmas.issuer-port:8500}")      private int    issuerPort;
    @Value("${dmas.timeout-seconds:30}")    private int    timeoutSeconds;
    private static final String FORWARDING_ID = "002202";

    public McDmasKeyExchange(DmasNetworkUtil net, HsmService hsm, DmasKekRepository kekRepo, DmasAcqKeyRepository acqKeyRepo, DmasJposServer dmasJposServer) {
        this.net = net;
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.acqKeyRepo = acqKeyRepo;
        this.dmasJposServer = dmasJposServer;
    }

    public Map<String,Object> exchangePek(String memberGroupId) throws Exception {
        return exchange(memberGroupId, "PEK", "161");
    }


    private Map<String,Object> exchange(String mgid, String keyType, String de070) throws Exception {
        DmasKek kek = kekRepo.findByMemberGroupId(mgid)
                .orElseThrow(() -> new IllegalStateException("KEK introuvable pour " + mgid));
        if (kek.getKekClear() == null)
            throw new IllegalStateException("kek_clear absent pour " + mgid);

        int keyLen = (kek.getKeyLength() != null) ? kek.getKeyLength() : 24;

        // 1. Le RESEAU (acquereur) genere le PEK, chiffre sous KEK
        HsmService.KeyResult gen = hsm.generateWorkingKey(keyType, keyLen, kek.getKekClear());

        // 2. Construire le DE48 subelement 11 (Key Exchange Block officiel)
        KeyExchangeBlock keb = new KeyExchangeBlock();
        keb.keyClassId      = KeyExchangeBlock.KEY_CLASS_PIN; // PK
        keb.keyIndex        = "00";
        keb.keyCycle        = "00";
        keb.encryptedKeyHex = gen.keyUnderKekHex;
        keb.kcv             = gen.kcv;
        String de048 = keb.buildDe48();

        // 3. Network Data (DE63) : Banknet Reference Number genere par le reseau
        String banknetRef = "BNET" + net.generateStan();

        // 4. Construire et envoyer le 0800 (DE70=161)
        String stan = net.generateStan();
        String dt   = new SimpleDateFormat("MMddHHmmss").format(new Date());
        ISOMsg req = new ISOMsg();
        req.setPackager(net.getPackager());
        req.setMTI("0800");
        req.set(2,  mgid);           // Member Group ID
        req.set(7,  dt);
        req.set(11, stan);
        req.set(33, FORWARDING_ID);  // 002202 = reseau MC
        req.set(48, de048);          // Key Exchange Block subelement 11
        req.set(63, banknetRef);     // Network Data
        req.set(70, de070);          // 161

        // LOG detaille de tous les DE du 0800
        log.info("[DMAS-ACQ] === 0800 PEK exchange (DE70=161) ===");
        log.info("[DMAS-ACQ] DE2  Member Group ID      = {}", mgid);
        log.info("[DMAS-ACQ] DE7  Transmission DateTime = {}", dt);
        log.info("[DMAS-ACQ] DE11 STAN                  = {}", stan);
        log.info("[DMAS-ACQ] DE33 Forwarding Inst ID    = {}", FORWARDING_ID);
        log.info("[DMAS-ACQ] DE63 Network Data          = {}", banknetRef);
        log.info("[DMAS-ACQ] DE70 Network Mgmt Code     = {}", de070);
        keb.logDetail("0800 envoye (DE48)");

        String reqHex = ISOUtil.hexString(req.pack());
        ISOMsg resp = dmasJposServer.pushAndWait(req, timeoutSeconds);
        String rc = net.safeGet(resp, 39);
        boolean ok = "00".equals(rc);

        log.info("[DMAS-ACQ] <- 0810 DE39={} ok={}", rc, ok);

        Map<String,Object> r = new LinkedHashMap<>();
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

        // Persister le PEK sous LMK reseau (dmas_acq_keys) si succes
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
            log.info("[DMAS-ACQ] {} persiste dans dmas_acq_keys (KCV={})", keyType, gen.kcv);
        }

        // 0820 PEK exchange advice : confirme succes (164) ou echec (165) au customer
        try {
            String adviceDe70 = ok ? "164" : "165";
            String adviceStan = net.generateStan();
            String adviceDt   = new SimpleDateFormat("MMddHHmmss").format(new Date());

            ISOMsg advice = new ISOMsg();
            advice.setPackager(net.getPackager());
            advice.setMTI("0820");
            advice.set(2,  mgid);
            advice.set(7,  adviceDt);
            advice.set(11, adviceStan);
            advice.set(33, FORWARDING_ID);
            advice.set(63, banknetRef);
            advice.set(70, adviceDe70);

            dmasJposServer.pushOnActiveSession(advice);
            log.info("[DMAS-ACQ] 0820 advice envoye : DE70={} (ok={})", adviceDe70, ok);
            r.put("de070_advice", adviceDe70);
            r.put("advice_sent", true);
        } catch (Exception e) {
            log.error("[DMAS-ACQ] Echec envoi 0820 advice : {}", e.getMessage(), e);
            r.put("advice_sent", false);
        }

        return r;
    }
}
