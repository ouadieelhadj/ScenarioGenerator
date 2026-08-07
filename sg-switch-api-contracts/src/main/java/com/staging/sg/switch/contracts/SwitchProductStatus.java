package com.staging.sg.member.contracts;

import java.time.Instant;

public record SwitchProductStatus(
        String schemaVersion,
        String productCode,
        String productName,
        String backendBoundary,
        String status,
        Instant checkedAt) {
}
