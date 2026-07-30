package com.staging.sg.waypos.simulator.api;

import com.staging.sg.waypos.simulator.service.WayPosSimulatorClient;
import com.staging.sg.waypos.simulator.service.WayPosSimulatorScenarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/simulator/v1")
public class WayPosSimulatorController {
    private final WayPosSimulatorClient client;
    private final WayPosSimulatorScenarioService scenarios;

    public WayPosSimulatorController(
            WayPosSimulatorClient client,
            WayPosSimulatorScenarioService scenarios) {
        this.client = client;
        this.scenarios = scenarios;
    }

    @PostMapping("/scenarios/{scenario}")
    public ResponseEntity<?> scenario(
            @org.springframework.web.bind.annotation.PathVariable
            String scenario,
            @RequestBody SimulatorScenarioRequest request) {
        try {
            return ResponseEntity.ok(scenarios.run(scenario, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getClass().getSimpleName()
                            + ": " + e.getMessage()));
        }
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> send(@RequestBody SimulatorTransactionRequest request) {
        try {
            return ResponseEntity.ok(client.send(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @PostMapping("/transactions/repeat")
    public ResponseEntity<?> repeat(
            @RequestParam(name = "terminalId", required = false)
            String terminalId,
            @RequestParam(name = "macEnabled", required = false)
            Boolean macEnabled) {
        try {
            return ResponseEntity.ok(client.repeat(terminalId, macEnabled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getClass().getSimpleName()
                            + ": " + e.getMessage()));
        }
    }

    @PostMapping("/key-change")
    public ResponseEntity<?> keyChange(
            @RequestParam(name = "confirm", defaultValue = "true") boolean confirm) {
        try {
            return ResponseEntity.ok(client.keyChange(confirm));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "module", "wayPosSimulator");
    }
}
