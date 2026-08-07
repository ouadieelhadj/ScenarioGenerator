package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabProductStatus(
        String schemaVersion,
        String productCode,
        String productName,
        String backendBoundary,
        String status,
        Instant checkedAt) {
}
