package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabOnlineScenarioResult(String executionId, String scenarioCode, String networkCode,
                                            String status, String responseCode, boolean successful,
                                            String correlationId, Instant completedAt) { }
