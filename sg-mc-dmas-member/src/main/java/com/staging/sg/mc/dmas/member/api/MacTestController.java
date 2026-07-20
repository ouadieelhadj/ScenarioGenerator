package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.iso.McPackagerEbcdic;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.McMacBuilder;
import com.staging.sg.common.repository.McDmasMemberKeyRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test MAC isolé (sans réseau).
 * Construit un message, calcule le MAC sur les champs configurés
 * (dmas.mac.fields / dmas.mac.representation), puis le vérifie.
 */
@RestController
@RequestMapping("/api/admin/dmas/crypto")
public class MacTestController {

    private static final Logger log = LoggerFactory.getLogger(MacTestController.class);

    private final HsmService hsm;
    private final McDmasMemberKeyRepository acqKeyRepo;
    private final McPackagerEbcdic packager = new McPackagerEbcdic();

    @Value("${dmas.mac.fields:4,11,37,41,42}")        private String macFields;
    @Value("${dmas.mac.representation:ebcdic}")       private String macRepr;

    public MacTestController(HsmService hsm, McDmasMemberKeyRepository acqKeyRepo) {
        this.hsm = hsm;
        this.acqKeyRepo = acqKeyRepo;
    }

    // GET /api/admin/dmas/crypto/mac?memberGroupId=TESTGRP01
    @GetMapping("/mac")
    public ResponseEntity<?> mac(@RequestParam(defaultValue = "TESTGRP01") String memberGroupId) {
        try {
            // 1. Récupérer la MAK sous LMK acquéreur
            McDmasMemberKey mak = acqKeyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "MAK", "ACTIVE")
                    .orElseThrow(() -> new IllegalStateException("MAK introuvable pour " + memberGroupId));

            // 2. Construire un message de test avec quelques champs
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0100");
            msg.set(4,  "000000010000");      // montant
            msg.set(11, "123456");            // STAN
            msg.set(37, "123456789012");      // RRN
            msg.set(41, "TERM0001");          // terminal
            msg.set(42, "MERCHANT00012345");  // merchant

            // 3. Construire le MAC input + calculer le MAC
            byte[] macInput = McMacBuilder.build(msg, macFields, macRepr);
            byte[] mac = hsm.generateMac(macInput, mak.getKeyUnderLmk(), mak.getKcv(), mak.getKeyLength());

            // 4. Vérifier (recalcul + compare)
            boolean verified = hsm.verifyMac(macInput, mak.getKeyUnderLmk(), mak.getKcv(), mak.getKeyLength(), mac);

            Map<String,Object> r = new LinkedHashMap<>();
            r.put("member_group_id", memberGroupId);
            r.put("mac_fields", macFields);
            r.put("mac_representation", macRepr);
            r.put("mak_kcv", mak.getKcv());
            r.put("mac_input_hex", ISOUtil.hexString(macInput));
            r.put("mac_input_len", macInput.length);
            r.put("mac_hex", ISOUtil.hexString(mac));
            r.put("mac_len", mac.length);
            r.put("verified", verified);
            log.info("[MAC-TEST] mac={} verified={}", ISOUtil.hexString(mac), verified);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[MAC-TEST] failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
