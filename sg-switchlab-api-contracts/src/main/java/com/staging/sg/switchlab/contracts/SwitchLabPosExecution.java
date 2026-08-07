package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.util.Map;

public record SwitchLabPosExecution(
        String executionId,
        String operation,
        String status,
        String verdict,
        String correlationId,
        Instant startedAt,
        Instant completedAt,
        long elapsedMillis,
        Map<String, Object> requestSummary,
        Map<String, Object> response,
        String expectedResult) {
}
