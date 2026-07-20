package com.staging.sg.mc.dmas.mastercard.api;

import com.staging.sg.common.entity.McDmasCard;
import com.staging.sg.common.repository.McDmasCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestion des cartes côté BANQUE émettrice (issuer).
 * Crée des cartes avec PIN + solde, sert au moteur de décision du 0100.
 */
@RestController
@RequestMapping("/api/admin/dmas/cards")
public class CardController {

    private static final Logger log = LoggerFactory.getLogger(CardController.class);

    private final McDmasCardRepository cardRepo;

    public CardController(McDmasCardRepository cardRepo) {
        this.cardRepo = cardRepo;
    }

    public static class CardRequest {
        public String pan;
        public String pin;
        public Long   balance;   // centimes
        public String currency;  // défaut 840
        public String expiry;    // YYMM
    }

    public static class BalanceRequest {
        public Long balance;     // nouveau solde en centimes
    }

    /** Créer (ou mettre à jour) une carte. */
    @PostMapping
    public ResponseEntity<?> createCard(@RequestBody CardRequest req) {
        try {
            if (req.pan == null || req.pin == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "pan et pin requis"));
            }
            McDmasCard card = cardRepo.findByPan(req.pan).orElseGet(McDmasCard::new);
            card.setPan(req.pan);
            card.setPin(req.pin);
            card.setBalance(req.balance != null ? req.balance : 0L);
            card.setCurrency(req.currency != null ? req.currency : "840");
            card.setExpiry(req.expiry);
            card.setStatus("ACTIVE");
            card.setUpdatedAt(LocalDateTime.now());
            cardRepo.save(card);
            log.info("[DMAS-ISS] Carte créée/MAJ PAN=***{} balance={} centimes",
                    req.pan.length() >= 4 ? req.pan.substring(req.pan.length()-4) : req.pan, card.getBalance());
            return ResponseEntity.ok(toMap(card));
        } catch (Exception e) {
            log.error("[DMAS-ISS] createCard failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** Mettre à jour le solde d'une carte. */
    @PostMapping("/{pan}/balance")
    public ResponseEntity<?> setBalance(@PathVariable String pan, @RequestBody BalanceRequest req) {
        try {
            McDmasCard card = cardRepo.findByPan(pan).orElse(null);
            if (card == null) return ResponseEntity.status(404).body(Map.of("error", "Carte introuvable"));
            card.setBalance(req.balance != null ? req.balance : 0L);
            card.setUpdatedAt(LocalDateTime.now());
            cardRepo.save(card);
            log.info("[DMAS-ISS] Solde MAJ PAN=***{} -> {} centimes",
                    pan.length() >= 4 ? pan.substring(pan.length()-4) : pan, card.getBalance());
            return ResponseEntity.ok(toMap(card));
        } catch (Exception e) {
            log.error("[DMAS-ISS] setBalance failed : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** Consulter une carte. */
    @GetMapping("/{pan}")
    public ResponseEntity<?> getCard(@PathVariable String pan) {
        McDmasCard card = cardRepo.findByPan(pan).orElse(null);
        if (card == null) return ResponseEntity.status(404).body(Map.of("error", "Carte introuvable"));
        return ResponseEntity.ok(toMap(card));
    }

    private Map<String,Object> toMap(McDmasCard c) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("pan", c.getPan());
        m.put("balance_centimes", c.getBalance());
        m.put("currency", c.getCurrency());
        m.put("expiry", c.getExpiry());
        m.put("status", c.getStatus());
        return m;
    }
}
