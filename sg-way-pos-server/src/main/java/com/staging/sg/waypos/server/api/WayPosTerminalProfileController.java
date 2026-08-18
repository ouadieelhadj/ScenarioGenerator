package com.staging.sg.waypos.server.api;

import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/waypos/v1/terminals")
public class WayPosTerminalProfileController {
    private final PosTerminalProfileRepository terminals;

    public WayPosTerminalProfileController(PosTerminalProfileRepository terminals) {
        this.terminals = terminals;
    }

    @PostMapping
    public ResponseEntity<?> provision(@RequestBody TerminalRequest request) {
        try {
            if (terminals.existsById(request.terminalId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Terminal already exists"));
            }
            String batch = request.initialBatchId() == null ? "000000" : request.initialBatchId();
            PosTerminalProfile terminal = "SOFTPOS".equals(request.terminalType())
                    ? PosTerminalProfile.provisionedSoftPos(
                            request.terminalId(), request.merchantId(),
                            request.memberId(), request.outletId(),
                            request.extendedSet(), request.macData(),
                            request.macRequired(), batch)
                    : PosTerminalProfile.provisioned(
                            request.terminalId(), request.merchantId(),
                            request.extendedSet(), request.macData(),
                            request.macRequired(), batch);
            terminals.save(terminal);
            return ResponseEntity.status(HttpStatus.CREATED).body(view(terminal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{terminalId}/enabled")
    public ResponseEntity<?> enabled(
            @PathVariable String terminalId, @RequestBody EnabledRequest request) {
        return terminals.findById(terminalId)
                .<ResponseEntity<?>>map(terminal -> {
                    terminal.setEnabled(request.enabled());
                    terminals.save(terminal);
                    return ResponseEntity.ok(view(terminal));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return terminals.findAll().stream().map(WayPosTerminalProfileController::view)
                .toList();
    }

    private static Map<String, Object> view(PosTerminalProfile value) {
        return Map.ofEntries(
                Map.entry("terminalId", value.getTerminalId()),
                Map.entry("merchantId", value.getMerchantId()),
                Map.entry("memberId", value.getMemberId()),
                Map.entry("outletId", value.getOutletId()),
                Map.entry("terminalType", value.getTerminalType()),
                Map.entry("enabled", value.isEnabled()),
                Map.entry("extendedSet", value.isExtendedSet()),
                Map.entry("macData", value.getMacData()),
                Map.entry("macRequired", value.isMacRequired()),
                Map.entry("batchId", value.getBatchId()),
                Map.entry("batchStatus", value.getBatchStatus()));
    }

    public record TerminalRequest(
            String terminalId, String merchantId, String memberId,
            String outletId, String terminalType, boolean extendedSet,
            String macData, boolean macRequired, String initialBatchId) {}

    public record EnabledRequest(boolean enabled) {}
}
