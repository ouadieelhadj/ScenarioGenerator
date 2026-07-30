package com.staging.sg.waypos.simulator.api;

import java.util.List;

public record SimulatorScenarioResponse(
        String scenario,
        boolean completed,
        String status,
        String batchId,
        List<SimulatorTransactionResponse> steps) {
}
