package com.staging.sg.acquirer.api;

import com.staging.sg.acquirer.service.LoadTestOrchestrationService;
import com.staging.sg.acquirer.service.ExecutionService;
import com.staging.sg.common.entity.Execution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    private final ExecutionService executionService;
    private final LoadTestOrchestrationService loadTestOrch;

    public ExecutionController(ExecutionService executionService,
                               LoadTestOrchestrationService loadTestOrch) {
        this.executionService = executionService;
        this.loadTestOrch = loadTestOrch;
    }

    // POST /api/executions/start/{testId}?mode=CHARGE&persist=true
    @PreAuthorize("hasAuthority('TPS_RUN')")
    @PostMapping("/start/{testId}")
    public ResponseEntity<?> start(@PathVariable Long testId,
                                    @RequestParam(defaultValue = "SIMPLE") String mode,
                                    @RequestParam(defaultValue = "false") boolean persist,
                                    Authentication auth) {
        try {
            Map<String, Object> result = executionService.start(
                    testId, auth.getName(), mode, persist);
            log.info("[API] Execution started — testId={} mode={} persist={} user={}",
                    testId, mode, persist, auth.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[API] Start execution error : {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/executions/stop/{executionId}
    @PreAuthorize("hasAuthority('TPS_RUN')")
    @PostMapping("/loadtest/{testId}")
    public ResponseEntity<?> loadtest(@PathVariable Long testId, Authentication auth) {
        try {
            Map<String, Object> result = loadTestOrch.start(testId, auth.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[API] loadtest error : {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('TPS_RUN')")
    @PostMapping("/stop/{executionId}")
    public ResponseEntity<?> stop(@PathVariable Long executionId) {
        try {
            return ResponseEntity.ok(executionService.stop(executionId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/executions/{executionId}/status
    @GetMapping("/{executionId}/status")
    public ResponseEntity<?> status(@PathVariable Long executionId) {
        try {
            return ResponseEntity.ok(executionService.getStatus(executionId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/executions/history
    @GetMapping("/history")
    public ResponseEntity<List<Execution>> history(Authentication auth) {
        try {
            return ResponseEntity.ok(executionService.getHistory(auth.getName()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GET /api/executions/admin/history
    @GetMapping("/admin/history")
    @PreAuthorize("hasAuthority('EXECUTION_VIEW')")
    public ResponseEntity<List<Execution>> adminHistory() {
        try {
            return ResponseEntity.ok(executionService.getAllHistory());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
