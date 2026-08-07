package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabClearingArtifact(String id, String networkCode, String fileName, String status,
                                        long recordCount, String amountChecksum, String evidenceReference,
                                        String correlationId, Instant receivedAt) { }
