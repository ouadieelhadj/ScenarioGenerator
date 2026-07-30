package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.service.WayPosWorkingKeyBootstrapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Security-officer integration point. Authentication/authorization is
 * expected at the deployment gateway. This API never accepts a clear key.
 */
@RestController
@RequestMapping("/api/admin/waypos/v1/terminals/{terminalId}/working-keys")
public class WayPosWorkingKeyController {
    private final WayPosWorkingKeyBootstrapService service;

    public WayPosWorkingKeyController(
            WayPosWorkingKeyBootstrapService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> activate(
            @PathVariable String terminalId,
            @RequestBody WorkingKeyRequest request) {
        try {
            PosTerminalProfile terminal = service.activate(
                    terminalId, request.keyType(), request.keyUnderLmk(),
                    request.kcv(), request.keyLength());
            return ResponseEntity.ok(Map.of(
                    "terminalId", terminal.getTerminalId(),
                    "keyType", request.keyType().toUpperCase(),
                    "kcv", request.kcv().toUpperCase(),
                    "keyLength", request.keyLength(),
                    "status", "ACTIVE"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    public record WorkingKeyRequest(
            String keyType, String keyUnderLmk,
            String kcv, Integer keyLength) {}
}
