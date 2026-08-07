package com.staging.sg.switchlab.contracts;

import java.time.Instant;
import java.time.LocalDate;

public record SwitchLabClearingEodResult(String executionId, String networkCode, LocalDate businessDate,
                                         String status, long recordCount, String evidenceReference,
                                         String correlationId, Instant completedAt) { }
