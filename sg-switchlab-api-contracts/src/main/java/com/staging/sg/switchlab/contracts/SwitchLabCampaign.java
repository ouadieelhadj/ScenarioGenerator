package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SwitchLabCampaign(String id, String name, String description, List<String> testCodes,
                                String profile, double minimumAvailabilityPercent,
                                long maximumResponseTimeMs, int durationSeconds, int targetTps,
                                int concurrency, Map<String, String> dataReferences,
                                String status, Instant createdAt) { }
