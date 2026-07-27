package com.staging.sg.swam.lis.common.model;

import java.time.LocalDate;
import java.util.UUID;

public record EodBatchResult(
        UUID correlationId,
        LocalDate businessDate,
        long readCount,
        long createdCount,
        long skippedCount,
        String status) {
}
