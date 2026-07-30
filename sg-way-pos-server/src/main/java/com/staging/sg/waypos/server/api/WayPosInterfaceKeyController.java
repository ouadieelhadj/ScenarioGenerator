package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.service.WayPosPinTranslationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/waypos/v1/interface-keys")
public class WayPosInterfaceKeyController {
    private final WayPosPinTranslationService service;

    public WayPosInterfaceKeyController(WayPosPinTranslationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> provision(@RequestBody InterfaceKeyRequest request) {
        try {
            service.provisionInterfaceKey(
                    request.interfaceCode(), request.pekUnderLmk(),
                    request.pekKcv(), request.pekLength());
            return ResponseEntity.accepted().body(Map.of(
                    "interfaceCode", request.interfaceCode(), "status", "ACTIVE"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record InterfaceKeyRequest(
            String interfaceCode, String pekUnderLmk, String pekKcv, int pekLength) {}
}
