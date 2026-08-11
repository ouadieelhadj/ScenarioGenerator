package com.staging.sg.onboarding.domain;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingOutboxEventTest {
    @Test
    void acquiringAndWay4UseIndependentEventsAndIdempotencyKeys() {
        UUID caseId=UUID.randomUUID();
        OnboardingOutboxEvent acquiring=OnboardingOutboxEvent.provisioningRequested(caseId,"{}","a".repeat(64));
        OnboardingOutboxEvent way4=OnboardingOutboxEvent.way4ExportRequested(caseId,"{}","b".repeat(64));
        assertEquals("merchant.provisioning.requested",acquiring.eventType());
        assertEquals("way4.export.requested",way4.eventType());
        assertNotEquals(acquiring.idempotencyKey(),way4.idempotencyKey());
    }
    @Test
    void appliesEightAttemptBudgetAndAllowsAuditedManualRetry() {
        OnboardingOutboxEvent event = OnboardingOutboxEvent.provisioningRequested(
                UUID.randomUUID(), "{\"schemaVersion\":\"2.0\"}", "a".repeat(64));
        for (int attempt = 1; attempt <= 8; attempt++) {
            Instant reserveAt = event.availableAt().plusMillis(1);
            event.reserve("worker-1", "corr-" + attempt, reserveAt);
            event.fail("TEMPORARY", "temporary failure", true);
            assertEquals(attempt, event.attempts());
            if (attempt < 8) assertEquals(OnboardingOutboxStatus.PENDING, event.status());
        }
        assertEquals(OnboardingOutboxStatus.FAILED_FINAL, event.status());
        event.manualRetry("operator", "cause corrected");
        assertEquals(OnboardingOutboxStatus.PENDING, event.status());
        assertEquals(0, event.attempts());
    }

    @Test
    void backoffIsThirtySecondsWithTwentyPercentJitterOnFirstFailure() {
        OnboardingOutboxEvent event = OnboardingOutboxEvent.provisioningRequested(
                UUID.randomUUID(), "{}", "b".repeat(64));
        Instant reservedAt = event.availableAt().plusMillis(1);
        event.reserve("worker", "correlation", reservedAt);
        Instant beforeFailure = Instant.now();
        event.fail("HTTP_503", "unavailable", true);
        long seconds = Duration.between(beforeFailure, event.availableAt()).toSeconds();
        assertTrue(seconds >= 23 && seconds <= 36,
                "first retry must stay within approximately +/-20% of 30 seconds");
    }
}
