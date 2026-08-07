package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.util.List;

public record SwitchLabCampaignReport(String executionId, String campaignId, String environmentId,
                                      String status, String verdict, double actualAvailabilityPercent,
                                      double expectedAvailabilityPercent, long elapsedMillis,
                                      int sampleCount, double errorRatePercent, long p95ResponseTimeMs,
                                      String correlationId, Instant startedAt, Instant completedAt,
                                      List<SwitchLabCampaignTestResult> results) { }
