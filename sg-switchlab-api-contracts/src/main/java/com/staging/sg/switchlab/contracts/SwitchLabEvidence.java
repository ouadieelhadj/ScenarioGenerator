package com.staging.sg.switchlab.contracts;

import java.time.Instant;

public record SwitchLabEvidence(String id, String sourceType, String name, String sourceReference,
                                int total, int passed, int failed, String verdict,
                                String correlationId, Instant importedAt) { }
