package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.dmas.acquirer.network.McDmasReversal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint de reversal 0400.
 * POST /api/admin/dmas/reversal avec body JSON :
 *   { "pan":"...", "amount":"000000050000", "processingCode":"000000",
 *     "originalStan":"944803", "originalDt":"0620103045" }
 */
@RestController
@RequestMapping("/api/admin/dmas")
public class ReversalController {

    private static final Logger log = LoggerFactory.getLogger(ReversalController.class);

    private final McDmasReversal reversal;

    public ReversalController(McDmasReversal reversal) {
        this.reversal = reversal;
    }

    public static class ReversalRequest {
        public String pan;
        public String amount;
        public String processingCode;
        public String originalStan;
        public String originalDt;
        public Boolean advice;   // true = Reversal Advice 0420 (Stand-In), sinon 0400
    }

    @PostMapping("/reversal")
    public ResponseEntity<?> reverse(@RequestBody ReversalRequest req) {
        try {
            if (req.pan == null || req.amount == null || req.originalStan == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Champs requis : pan, amount, originalStan"));
            }
            Map<String,Object> result;
            if (Boolean.TRUE.equals(req.advice)) {
                result = reversal.sendReversalAdvice(
                        req.pan, req.amount, req.processingCode, req.originalStan, req.originalDt);
            } else {
                result = reversal.sendReversal(
                        req.pan, req.amount, req.processingCode, req.originalStan, req.originalDt);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[DMAS-REV] reversal failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
