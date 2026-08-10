package com.staging.sg.acquiring.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Way4ExportOutboxEventTest {
    @Test
    void leaseOwnerAloneCanCompleteAndRetryReleasesTheLease() {
        Instant now = Instant.parse("2026-08-10T16:00:00Z");
        Way4ExportOutboxEvent event = Way4ExportOutboxEvent.pending(
                UUID.randomUUID(), "way4:test", "{}", "a".repeat(64), now);

        event.reserve("worker-a", now, now.plusSeconds(120));
        assertEquals(Way4ExportOutboxStatus.PROCESSING, event.status());
        assertEquals(1, event.attempts());
        assertThrows(IllegalStateException.class,
                () -> event.completed("worker-b", UUID.randomUUID(), now.plusSeconds(1)));

        event.failed("worker-a", "temporary\nconnector error", true, false, now.plusSeconds(1));
        assertEquals(Way4ExportOutboxStatus.PENDING, event.status());
        assertEquals(now.plusSeconds(31), event.availableAt());

        event.reserve("worker-b", now.plusSeconds(31), now.plusSeconds(151));
        event.completed("worker-b", UUID.randomUUID(), now.plusSeconds(32));
        assertEquals(Way4ExportOutboxStatus.COMPLETED, event.status());
    }

    @Test
    void mappingErrorIsBlockedWithoutRetry() {
        Instant now = Instant.parse("2026-08-10T16:00:00Z");
        Way4ExportOutboxEvent event = Way4ExportOutboxEvent.pending(
                UUID.randomUUID(), "way4:mapping", "{}", "b".repeat(64), now);
        event.reserve("worker-a", now, now.plusSeconds(120));
        event.failed("worker-a", "missing AURA binding", false, true, now.plusSeconds(1));
        assertEquals(Way4ExportOutboxStatus.MAPPING_BLOCKED, event.status());
    }
}
