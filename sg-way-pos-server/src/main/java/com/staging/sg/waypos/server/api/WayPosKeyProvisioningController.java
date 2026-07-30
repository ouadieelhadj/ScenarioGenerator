package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.service.WayPosKeyExchangeService;
import org.jpos.iso.ISOUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Security-officer integration point. Authentication/authorization is expected
 * at the deployment gateway; no clear working key is accepted by this API.
 */
@RestController
@RequestMapping("/api/admin/waypos/v1/terminal-keys")
public class WayPosKeyProvisioningController {
    private final WayPosKeyExchangeService service;

    public WayPosKeyProvisioningController(WayPosKeyExchangeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> provision(@RequestBody ProvisionKeyRequest request) {
        try {
            byte[] block = request.ansiX917BlockHex() == null ? null
                    : ISOUtil.hex2byte(request.ansiX917BlockHex());
            service.provision(new WayPosKeyExchangeService.ProvisionedKey(
                    request.terminalId(), request.keyType(), request.keyId(),
                    request.algorithm(), request.kcv(), request.masterKeyId(),
                    request.masterKeyType(), block, request.keyUnderLmk(),
                    request.keyLength(), request.actionCode(), request.replacementKeyId()));
            return ResponseEntity.accepted().body(Map.of(
                    "terminalId", request.terminalId(),
                    "keyType", request.keyType(),
                    "keyId", request.keyId(),
                    "status", "PENDING"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record ProvisionKeyRequest(
            String terminalId,
            String keyType,
            String keyId,
            String algorithm,
            String kcv,
            String masterKeyId,
            String masterKeyType,
            String ansiX917BlockHex,
            String keyUnderLmk,
            Integer keyLength,
            String actionCode,
            String replacementKeyId) {}
}
