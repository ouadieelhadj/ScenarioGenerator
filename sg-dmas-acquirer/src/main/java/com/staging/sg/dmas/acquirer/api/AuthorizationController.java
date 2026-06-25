package com.staging.sg.dmas.acquirer.api;

import com.staging.sg.dmas.acquirer.network.McDmasAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint générique d'autorisation 0100.
 * POST /api/admin/dmas/auth avec body JSON :
 *   { "type":"purchase", "pan":"...", "amount":"000000010000",
 *     "pin":"1234", "terminalId":"TERM0001", "acceptorId":"MERCHANT00012345" }
 * Le "type" mappe vers DE3 (Processing Code) + DE61 sf7 (POS Transaction Status).
 */
@RestController
@RequestMapping("/api/admin/dmas")
public class AuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationController.class);

    private final McDmasAuthorization auth;

    public AuthorizationController(McDmasAuthorization auth) {
        this.auth = auth;
    }

    /** Body de la requête d'autorisation. */
    public static class AuthRequest {
        public String type;        // purchase, withdrawal, preauth, refund, balance_inquiry...
        public String pan;
        public String amount;      // n-12 ex "000000010000"
        public String pin;         // optionnel
        public String terminalId;  // optionnel
        public String acceptorId;  // optionnel
        public String entryMode;   // ECOM ou CARD_PRESENT (defaut CARD_PRESENT)
        public String transport;   // jpos (connexion permanente) ou socket (ephemere, defaut)
    }

    @PostMapping("/auth")
    public ResponseEntity<?> authorize(@RequestBody AuthRequest req) {
        try {
            if (req.type == null || req.pan == null || req.amount == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Champs requis manquants : type, pan, amount"));
            }
            Map<String,Object> result = auth.sendAuthorization(
                    req.type, req.pan, req.amount, req.pin, req.terminalId, req.acceptorId, req.entryMode,
                    req.transport);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("[DMAS-AUTH] type invalide : {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Type invalide : " + e.getMessage()));
        } catch (Exception e) {
            log.error("[DMAS-AUTH] auth failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
