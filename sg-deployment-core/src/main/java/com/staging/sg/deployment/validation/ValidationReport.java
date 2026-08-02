package com.staging.sg.deployment.validation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidationReport(
        String executionId,
        Instant checkedAt,
        String clientCode,
        String environmentCode,
        String shellType,
        List<ValidationCheck> checks
) {
    public static ValidationReport create(String clientCode, String environmentCode,
                                          String shellType, List<ValidationCheck> checks) {
        return new ValidationReport(UUID.randomUUID().toString(), Instant.now(), clientCode,
                environmentCode, shellType, List.copyOf(checks));
    }

    public boolean hasBlocking() {
        return checks.stream().anyMatch(check -> check.status() == CheckStatus.BLOCKING);
    }
}
