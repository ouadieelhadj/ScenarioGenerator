package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.McDmasKekRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap KEK côté ACQUEREUR.
 * Reçoit la KEK claire, la forme sous le LMK local (acquéreur),
 * et upsert la colonne kek_under_acq_lmk dans dmas_kek.
 * Body : {"memberGroupId":"TESTGRP01","kekClear":"0123..EF"}
 */
@RestController
@RequestMapping("/api/admin/dmas/kek")
public class KekBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(KekBootstrapController.class);

    private final JposHsmService hsm;
    private final McDmasKekRepository kekRepo;

    public KekBootstrapController(JposHsmService hsm, McDmasKekRepository kekRepo) {
        this.hsm = hsm;
        this.kekRepo = kekRepo;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody Map<String,String> body) {
        try {
            String mgid = body.get("memberGroupId");
            String kekClear = body.get("kekClear");
            if (mgid == null || kekClear == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "memberGroupId et kekClear requis"));
            }

            JposHsmService.KekUnderLmk formed = hsm.formKekUnderLmk(kekClear);

            McDmasKek kek = kekRepo.findByMemberGroupId(mgid).orElseGet(McDmasKek::new);
            kek.setMemberGroupId(mgid);
            kek.setKeyLength(kekClear.length() / 2);
            kek.setKekClear(kekClear);
            kek.setKekUnderAcqLmk(formed.underLmkHex);
            kek.setKcv(formed.kcv);
            kek.setStatus("ACTIVE");
            if (kek.getDescription() == null) kek.setDescription("KEK bootstrap (acquirer side)");
            kekRepo.save(kek);

            Map<String,Object> r = new LinkedHashMap<>();
            r.put("member_group_id", mgid);
            r.put("side", "ACQUIRER");
            r.put("kek_under_acq_lmk", formed.underLmkHex);
            r.put("kcv", formed.kcv);
            log.info("[KEK-BOOT] Acquirer — group={} KCV={}", mgid, formed.kcv);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[KEK-BOOT] bootstrap failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
