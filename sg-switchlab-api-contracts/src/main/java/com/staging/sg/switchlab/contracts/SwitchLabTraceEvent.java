package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabTraceEvent(
        String id,
        Instant timestamp,
        String correlationId,
        String category,
        String level,
        String component,
        String message) {
}
