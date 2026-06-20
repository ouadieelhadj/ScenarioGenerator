package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.common.entity.DmasAcqKey;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.DmasAcqKeyRepository;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test PIN block isolé (sans réseau).
 * Chiffre un PIN sous PEK (FORMAT00), puis le déchiffre, vérifie le round-trip.
 */
@RestController
@RequestMapping("/api/admin/dmas/crypto")
public class PinTestController {

    private static final Logger log = LoggerFactory.getLogger(PinTestController.class);

    private final HsmService hsm;
    private final DmasAcqKeyRepository acqKeyRepo;

    public PinTestController(HsmService hsm, DmasAcqKeyRepository acqKeyRepo) {
        this.hsm = hsm;
        this.acqKeyRepo = acqKeyRepo;
    }

    // GET /api/admin/dmas/crypto/pin?pin=1234&pan=4111111111111111
    @GetMapping("/pin")
    public ResponseEntity<?> pin(@RequestParam(defaultValue = "1234") String pin,
                                 @RequestParam(defaultValue = "4111111111111111") String pan,
                                 @RequestParam(defaultValue = "TESTGRP01") String memberGroupId) {
        try {
            DmasAcqKey pek = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "PEK", "ACTIVE")
                    .orElseThrow(() -> new IllegalStateException("PEK introuvable pour " + memberGroupId));

            // 1. Chiffrer le PIN sous PEK
            byte[] block = hsm.encryptPinBlock(pin, pan, pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());

            // 2. Déchiffrer
            String decrypted = hsm.decryptPinBlock(block, pan, pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());

            boolean ok = pin.equals(decrypted);

            Map<String,Object> r = new LinkedHashMap<>();
            r.put("pin_sent", pin);
            r.put("pan", pan);
            r.put("pek_kcv", pek.getKcv());
            r.put("pin_block_hex", ISOUtil.hexString(block));
            r.put("pin_block_len", block.length);
            r.put("pin_decrypted", decrypted);
            r.put("roundtrip_ok", ok);
            log.info("[PIN-TEST] pin={} block={} decrypted={} ok={}",
                    pin, ISOUtil.hexString(block), decrypted, ok);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[PIN-TEST] failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
