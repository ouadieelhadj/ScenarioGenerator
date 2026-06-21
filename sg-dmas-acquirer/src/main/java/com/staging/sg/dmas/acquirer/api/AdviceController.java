package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.dmas.acquirer.network.McDmasAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints d'advice 0120.
 *  POST /api/admin/dmas/advice      : notification transaction offline
 *  POST /api/admin/dmas/completion  : finalise une preauth
 */
@RestController
@RequestMapping("/api/admin/dmas")
public class AdviceController {

    private static final Logger log = LoggerFactory.getLogger(AdviceController.class);

    private final McDmasAdvice advice;

    public AdviceController(McDmasAdvice advice) {
        this.advice = advice;
    }

    public static class AdviceRequest {
        public String pan;
        public String amount;
        public String processingCode;
        public String pin;
        public String terminalId;
        public String acceptorId;
    }

    public static class CompletionRequest {
        public String pan;
        public String finalAmount;
        public String processingCode;
        public String originalStan;
        public String originalDt;
    }

    @PostMapping("/advice")
    public ResponseEntity<?> advice(@RequestBody AdviceRequest req) {
        try {
            if (req.pan == null || req.amount == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "pan et amount requis"));
            }
            return ResponseEntity.ok(advice.sendAdvice(
                    req.pan, req.amount, req.processingCode, req.pin, req.terminalId, req.acceptorId));
        } catch (Exception e) {
            log.error("[DMAS-ADV] advice failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/completion")
    public ResponseEntity<?> completion(@RequestBody CompletionRequest req) {
        try {
            if (req.pan == null || req.finalAmount == null || req.originalStan == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "pan, finalAmount, originalStan requis"));
            }
            return ResponseEntity.ok(advice.sendCompletion(
                    req.pan, req.finalAmount, req.processingCode, req.originalStan, req.originalDt));
        } catch (Exception e) {
            log.error("[DMAS-ADV] completion failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
