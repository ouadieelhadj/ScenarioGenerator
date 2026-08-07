package com.staging.sg.member.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchAcquiringOverview(
        String schemaVersion,
        String overallStatus,
        List<SwitchMemberServiceStatus> services,
        List<SwitchAcquiringFeature> features,
        Instant checkedAt,
        String correlationId) {
}
