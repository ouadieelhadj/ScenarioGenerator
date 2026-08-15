package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchLabFraudOverview(String schemaVersion, String workspace, String operatingMode,
                                     String overallStatus, boolean platformConfigured,
                                     List<SwitchLabFraudFeature> features, Instant checkedAt,
                                     String correlationId) {
}
