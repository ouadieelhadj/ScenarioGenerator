package com.staging.sg.swam.acquirer.api;

import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.SwamKekRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic crypto : valide le round-trip generate/import d'une cle de travail.
 * Sert a prouver que la ZAK SIMPLE longueur (P10=016) tient en 16 hex et
 * re-importe avec un KCV identique (crux de l'incr.2.2).
 * POST /api/admin/swam/hsm/selftest?type=MAK&single=true
 */
@RestController
@RequestMapping("/api/admin/swam/hsm")
public class HsmSelfTestController {

    private final JposHsmService hsm;
    private final SwamKekRepository kekRepo;

    public HsmSelfTestController(JposHsmService hsm, SwamKekRepository kekRepo) {
        this.hsm = hsm; this.kekRepo = kekRepo;
    }

    @PostMapping("/selftest")
    public ResponseEntity<?> selftest(@RequestParam(defaultValue = "TESTGRP01") String memberGroupId,
                                      @RequestParam(defaultValue = "MAK") String type,
                                      @RequestParam(defaultValue = "true") boolean single) {
        try {
            SwamKek kek = kekRepo.findByMemberGroupId(memberGroupId)
                    .orElseThrow(() -> new IllegalStateException("KEK introuvable (bootstrap acquereur d'abord)"));
            if (kek.getKekClear() == null)
                throw new IllegalStateException("kek_clear absent : bootstrap acquereur d'abord");

            HsmService.KeyResult gen = single
                    ? hsm.generateWorkingKeySingle(type, kek.getKekClear())
                    : hsm.generateWorkingKey(type, 16, kek.getKekClear());
            HsmService.KeyResult imp = single
                    ? hsm.importWorkingKeySingle(type, gen.keyUnderKekHex, kek.getKekClear())
                    : hsm.importWorkingKey(type, gen.keyUnderKekHex, kek.getKekClear(), 16);

            boolean match = gen.kcv.equalsIgnoreCase(imp.kcv);
            int hlen = gen.keyUnderKekHex.length();

            Map<String,Object> r = new LinkedHashMap<>();
            r.put("key_type", type);
            r.put("single_length", single);
            r.put("under_kek_hex", gen.keyUnderKekHex);
            r.put("under_kek_hex_len", hlen);
            r.put("fits_p10_016", single && hlen == 16);
            r.put("kcv_generated", gen.kcv);
            r.put("kcv_reimported", imp.kcv);
            r.put("round_trip_ok", match);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
