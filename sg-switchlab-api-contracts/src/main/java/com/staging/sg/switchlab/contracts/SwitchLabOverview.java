package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchLabOverview(
        String schemaVersion,
        SwitchLabEnvironmentReference environment,
        String overallStatus,
        long availableComponents,
        long degradedComponents,
        long unavailableComponents,
        List<SwitchLabComponentHealth> components,
        Instant checkedAt,
        String correlationId) {
}
