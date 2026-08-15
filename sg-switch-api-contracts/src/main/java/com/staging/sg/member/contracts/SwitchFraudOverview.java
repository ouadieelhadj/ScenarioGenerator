package com.staging.sg.member.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchFraudOverview(String schemaVersion, String workspace, String operatingMode,
                                  String overallStatus, boolean platformConfigured,
                                  List<SwitchFraudFeature> features, Instant checkedAt,
                                  String correlationId) {
}
