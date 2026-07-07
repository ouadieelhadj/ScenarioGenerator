package com.staging.sg.swam.acquirer.api;

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
 * Bootstrap KEK SWAM cote MEMBRE/BANQUE (module acquereur).
 * Forme la meme KEK claire sous le LMK local, upsert kek_under_acq_lmk + kek_clear
 * (kek_clear est requis pour importer les cles ZPK/ZAK recues du switch).
 * Body : {"memberGroupId":"TESTGRP01","kekClear":"0123..EFx3"}
 */
@RestController
@RequestMapping("/api/admin/swam/kek")
public class KekBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(KekBootstrapController.class);
    private final JposHsmService hsm;
    private final SwamKekRepository kekRepo;

    public KekBootstrapController(JposHsmService hsm, SwamKekRepository kekRepo) {
        this.hsm = hsm; this.kekRepo = kekRepo;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody Map<String,String> body) {
        try {
            String mgid = body.get("memberGroupId");
            String kekClear = body.get("kekClear");
            if (mgid == null || kekClear == null)
                return ResponseEntity.badRequest().body(Map.of("error","memberGroupId et kekClear requis"));

            JposHsmService.KekUnderLmk formed = hsm.formKekUnderLmk(kekClear);

            SwamKek kek = kekRepo.findByMemberGroupId(mgid).orElseGet(SwamKek::new);
            kek.setMemberGroupId(mgid);
            kek.setKeyLength(kekClear.length() / 2);
            kek.setKekClear(kekClear);
            kek.setKekUnderAcqLmk(formed.underLmkHex);
            if (kek.getKcv() == null) kek.setKcv(formed.kcv);
            kek.setStatus("ACTIVE");
            if (kek.getDescription() == null) kek.setDescription("KEK bootstrap SWAM (acquirer side)");
            kekRepo.save(kek);

            Map<String,Object> r = new LinkedHashMap<>();
            r.put("member_group_id", mgid);
            r.put("side", "ACQUIRER");
            r.put("kek_under_acq_lmk", formed.underLmkHex);
            r.put("kcv", formed.kcv);
            log.info("[SWAM-KEK-BOOT] Acquirer — group={} KCV={}", mgid, formed.kcv);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            log.error("[SWAM-KEK-BOOT] acquirer bootstrap failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
