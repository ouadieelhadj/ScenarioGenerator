package com.staging.sg.visa.online.member.service;

import java.time.Instant;
import java.util.Map;

public record VisaOnlineJournalView(String transactionId, String correlationId,
        String idempotencyKey, String maskedPan, String requestMti, String responseMti,
        String stan, String rrn, String responseCode, String authorizationCode,
        long amountMinor, String currency, String aci, String visaTransactionId,
        String validationCode, String provenance, Instant createdAt,
        Map<String, String> clearingData) {
}
