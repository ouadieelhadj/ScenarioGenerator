package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.common.iso.crypto.HsmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dmas/crypto")
public class CryptoTestController {

    private static final Logger log = LoggerFactory.getLogger(CryptoTestController.class);

    private final HsmService hsm;

    @Value("${dmas.test.kek-clear:0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF}")
    private String testKekClear;

    public CryptoTestController(HsmService hsm) {
        this.hsm = hsm;
    }

    @GetMapping("/genkey")
    public ResponseEntity<?> genkey(@RequestParam(defaultValue = "PEK") String type,
                                    @RequestParam(defaultValue = "24") int len) {
        try {
            Map<String,Object> r = new LinkedHashMap<>();

            HsmService.KeyResult gen = hsm.generateWorkingKey(type, len, testKekClear);
            r.put("type", type);
            r.put("key_length_bytes", len);
            r.put("kek_clear", testKekClear);
            r.put("key_under_kek", gen.keyUnderKekHex);
            r.put("kcv_generated", gen.kcv);
            r.put("thales_a0", gen.thalesCommand);

            HsmService.KeyResult imp = hsm.importWorkingKey(gen.keyUnderKekHex, testKekClear, len);
            r.put("kcv_imported", imp.kcv);
            r.put("thales_a6", imp.thalesCommand);

            boolean kcvMatch = gen.kcv.equals(imp.kcv);
            r.put("roundtrip_ok", kcvMatch);

            log.info("[CRYPTO-TEST] genkey {} — KCV gen={} imp={} match={}",
                    type, gen.kcv, imp.kcv, kcvMatch);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[CRYPTO-TEST] genkey failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
