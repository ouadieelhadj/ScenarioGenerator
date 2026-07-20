package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.mc.dmas.member.network.McDmasKeyExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pilotage de l'échange de clés PEK/MAK côté acquéreur.
 * Protégé ADMIN via /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/dmas/keyexchange")
public class KeyExchangeController {

    private static final Logger log = LoggerFactory.getLogger(KeyExchangeController.class);

    private final McDmasKeyExchange keyExchange;

    public KeyExchangeController(McDmasKeyExchange keyExchange) {
        this.keyExchange = keyExchange;
    }

    @PostMapping("/pek")
    public ResponseEntity<?> pek(@RequestParam(defaultValue = "TESTGRP01") String memberGroupId) {
        return run(() -> keyExchange.exchangePek(memberGroupId), "PEK");
    }

    private interface ExchangeCall { Map<String,Object> call() throws Exception; }

    private ResponseEntity<?> run(ExchangeCall c, String label) {
        try {
            return ResponseEntity.ok(c.call());
        } catch (Exception e) {
            log.error("[DMAS-ACQ] keyexchange {} failed : {}", label, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
