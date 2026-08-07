package com.staging.sg.member.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchDomainOverview(
        String schemaVersion,
        String domain,
        String overallStatus,
        List<SwitchMemberServiceStatus> services,
        List<SwitchDomainFeature> features,
        Instant checkedAt,
        String correlationId) {
}
