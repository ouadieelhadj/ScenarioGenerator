package com.staging.sg.mc.sms.issuer.api;

import com.staging.sg.mc.sms.issuer.network.McSmsIssKeyExchange;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consultation du simulateur Mastercard : derniere cle livree.
 * Sert a verifier, cote test, que le membre a bien importe la meme cle.
 *
 *   GET /api/admin/mc/sim/last-key
 */
@RestController
@RequestMapping("/api/admin/mc/sim")
public class McIssKeyController {

    private final McSmsIssKeyExchange keyExchange;

    public McIssKeyController(McSmsIssKeyExchange keyExchange) {
        this.keyExchange = keyExchange;
    }

    @GetMapping("/last-key")
    public Map<String, Object> lastKey() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("zmk",           keyExchange.getZmkHex());
        r.put("clear_key",     keyExchange.getLastClearKey());
        r.put("encrypted_key", keyExchange.getLastEncryptedKey());
        r.put("kcv",           keyExchange.getLastKcv());
        String kcv = keyExchange.getLastKcv();
        r.put("kcv_sent",      kcv == null ? null : kcv.substring(0, Math.min(4, kcv.length())));
        return r;
    }
}
