package com.staging.sg.swam.issuer.api;

import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.SwamKekRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap KEK SWAM cote CENTRE/SWITCH (module issuer).
 * Recoit la KEK claire, la forme sous le LMK local, et upsert la colonne
 * kek_under_iss_lmk dans swam_kek (table partagee acq/iss).
 *
 * Convention SWAM = miroir DMAS : KEK = ZMK (racine), sous laquelle seront
 * generees plus tard la ZPK (P16) et la ZAK (P10) a l'incr.2.2.
 *
 * Securite : le module swam-issuer est en permitAll (POC incr.1) ; a aligner
 * sur DMAS (JWT+CORS) quand l'IHM pilotera SWAM.
 *
 * Body : {"memberGroupId":"TESTGRP01","kekClear":"0123456789ABCDEF...x3"}
 */
@RestController
@RequestMapping("/api/admin/swam/kek")
public class KekBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(KekBootstrapController.class);

    private final JposHsmService hsm;
    private final SwamKekRepository kekRepo;

    public KekBootstrapController(JposHsmService hsm, SwamKekRepository kekRepo) {
        this.hsm = hsm;
        this.kekRepo = kekRepo;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody Map<String, String> body) {
        try {
            String mgid = body.get("memberGroupId");
            String kekClear = body.get("kekClear");
            if (mgid == null || kekClear == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "memberGroupId et kekClear requis"));
            }

            JposHsmService.KekUnderLmk formed = hsm.formKekUnderLmk(kekClear);

            SwamKek kek = kekRepo.findByMemberGroupId(mgid).orElseGet(SwamKek::new);
            kek.setMemberGroupId(mgid);
            kek.setKeyLength(kekClear.length() / 2);
            if (kek.getKekClear() == null) kek.setKekClear(kekClear);
            kek.setKekUnderIssLmk(formed.underLmkHex);
            if (kek.getKcv() == null) kek.setKcv(formed.kcv);
            kek.setStatus("ACTIVE");
            if (kek.getDescription() == null) kek.setDescription("KEK bootstrap SWAM (issuer side)");
            kekRepo.save(kek);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("member_group_id", mgid);
            r.put("side", "ISSUER");
            r.put("kek_under_iss_lmk", formed.underLmkHex);
            r.put("kcv", formed.kcv);
            log.info("[SWAM-KEK-BOOT] Issuer — group={} KCV={}", mgid, formed.kcv);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[SWAM-KEK-BOOT] bootstrap failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
