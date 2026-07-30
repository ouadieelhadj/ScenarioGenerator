package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.domain.PosCard;
import com.staging.sg.waypos.server.repository.PosCardRepository;
import com.staging.sg.waypos.server.service.PanProtectionService;
import com.staging.sg.waypos.server.service.WayPosProtectedKeyValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/waypos/v1/cards")
public class WayPosCardController {
    private final PosCardRepository cards;
    private final PanProtectionService panProtection;
    private final WayPosProtectedKeyValidationService keyValidation;

    public WayPosCardController(
            PosCardRepository cards, PanProtectionService panProtection,
            WayPosProtectedKeyValidationService keyValidation) {
        this.cards = cards;
        this.panProtection = panProtection;
        this.keyValidation = keyValidation;
    }

    @PostMapping
    public ResponseEntity<?> provision(@RequestBody CardRequest request) {
        try {
            validate(request);
            if (request.mdkUnderLmk() != null) {
                keyValidation.requireValid(
                        "MDK", request.mdkUnderLmk(),
                        request.mdkKcv(), request.mdkLength());
            }
            String hash = panProtection.hash(request.pan());
            if (cards.existsById(hash)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Card already exists"));
            }
            PosCard card = PosCard.provisioned(
                    hash, panProtection.mask(request.pan()), request.expiryYymm(),
                    request.currency(), request.availableBalance(),
                    request.pinPvv(), request.pinPvki(),
                    request.mdkUnderLmk(), request.mdkKcv(), request.mdkLength(),
                    request.panSequenceNumber(), request.arpcArcHex());
            cards.save(card);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "panMasked", panProtection.mask(request.pan()),
                    "status", "ACTIVE"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static void validate(CardRequest request) {
        if (request.expiryYymm() == null
                || !request.expiryYymm().matches("\\d{4}")
                || request.currency() == null || !request.currency().matches("\\d{3}")
                || request.availableBalance() < 0) {
            throw new IllegalArgumentException("Invalid card business data");
        }
        boolean hasPvv = request.pinPvv() != null;
        if ((hasPvv && !request.pinPvv().matches("\\d{4}"))
                || (hasPvv && request.pinPvki() == null)
                || (request.pinPvki() != null
                && (request.pinPvki() < 0 || request.pinPvki() > 9))) {
            throw new IllegalArgumentException(
                    "PVV requires a valid PVKI; PVKI may be pre-provisioned for enrolment");
        }
        boolean anyEmv = request.mdkUnderLmk() != null || request.mdkKcv() != null
                || request.mdkLength() != null || request.arpcArcHex() != null;
        if (anyEmv && (request.mdkUnderLmk() == null
                || request.mdkKcv() == null
                || !request.mdkKcv().matches("(?i)[0-9a-f]{6}")
                || request.mdkLength() == null
                || (request.mdkLength() != 16 && request.mdkLength() != 24)
                || request.arpcArcHex() == null
                || !request.arpcArcHex().matches("(?i)[0-9a-f]{4}"))) {
            throw new IllegalArgumentException(
                    "MDK, KCV, length and ARPC ARC must be provisioned together");
        }
    }

    public record CardRequest(
            String pan,
            String expiryYymm,
            String currency,
            long availableBalance,
            String pinPvv,
            Integer pinPvki,
            String mdkUnderLmk,
            String mdkKcv,
            Integer mdkLength,
            String panSequenceNumber,
            String arpcArcHex) {}
}
