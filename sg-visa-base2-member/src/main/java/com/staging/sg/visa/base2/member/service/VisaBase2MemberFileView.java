package com.staging.sg.visa.base2.member.service;

import java.time.Instant;
import java.util.List;

public record VisaBase2MemberFileView(String fileId, String transactionId,
        String correlationId, String arn, String status, int recordCount,
        String sha256, String networkStatus, boolean replayed, Instant createdAt,
        List<String> errors) {
    public VisaBase2MemberFileView { errors = errors == null ? List.of() : List.copyOf(errors); }
}
