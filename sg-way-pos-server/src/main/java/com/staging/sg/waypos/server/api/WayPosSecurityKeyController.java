package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.domain.PosSecurityKey;
import com.staging.sg.waypos.server.repository.PosSecurityKeyRepository;
import com.staging.sg.waypos.server.service.WayPosProtectedKeyValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/waypos/v1/security-keys")
public class WayPosSecurityKeyController {
    private final PosSecurityKeyRepository keys;
    private final WayPosProtectedKeyValidationService keyValidation;

    public WayPosSecurityKeyController(
            PosSecurityKeyRepository keys,
            WayPosProtectedKeyValidationService keyValidation) {
        this.keys = keys;
        this.keyValidation = keyValidation;
    }

    @PostMapping
    public ResponseEntity<?> provision(@RequestBody SecurityKeyRequest request) {
        try {
            if (!valid(request)) {
                throw new IllegalArgumentException("Invalid key metadata");
            }
            keyValidation.requireValid(
                    request.keyType(), request.keyUnderLmk(),
                    request.kcv(), request.keyLength());
            PosSecurityKey key = keys.findById(request.keyCode())
                    .orElseGet(() -> PosSecurityKey.active(
                            request.keyCode(), request.keyType(), request.keyUnderLmk(),
                            request.kcv().toUpperCase(), request.keyLength()));
            key.replace(request.keyType(), request.keyUnderLmk(),
                    request.kcv().toUpperCase(), request.keyLength());
            keys.save(key);
            return ResponseEntity.accepted().body(Map.of(
                    "keyCode", request.keyCode(), "status", "ACTIVE"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static boolean valid(SecurityKeyRequest request) {
        return request.keyCode() != null && !request.keyCode().isBlank()
                && request.keyType() != null && !request.keyType().isBlank()
                && request.keyUnderLmk() != null && !request.keyUnderLmk().isBlank()
                && request.kcv() != null && request.kcv().matches("(?i)[0-9a-f]{6}")
                && (request.keyLength() == 8 || request.keyLength() == 16);
    }

    public record SecurityKeyRequest(
            String keyCode, String keyType, String keyUnderLmk,
            String kcv, int keyLength) {}
}
