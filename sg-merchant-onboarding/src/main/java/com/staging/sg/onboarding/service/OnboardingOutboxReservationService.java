package com.staging.sg.onboarding.service;

import com.staging.sg.onboarding.domain.OnboardingOutboxEvent;
import com.staging.sg.onboarding.repository.OnboardingOutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OnboardingOutboxReservationService {
    private final OnboardingOutboxEventRepository events;
    private final String workerId;

    public OnboardingOutboxReservationService(OnboardingOutboxEventRepository events,
            @Value("${merchant-onboarding.outbox.worker-id:${spring.application.name}}") String workerId) {
        this.events = events;
        this.workerId = workerId;
    }

    @Transactional
    public List<ReservedEvent> reserve(int limit) {
        if (limit < 1 || limit > 100)
            throw new IllegalArgumentException("Outbox reservation limit must be between 1 and 100");
        Instant now = Instant.now();
        for (OnboardingOutboxEvent exhausted : events.lockExpiredExhausted(now, limit)) {
            exhausted.fail("LEASE_EXPIRED_AFTER_MAX_ATTEMPTS",
                    "Processing lease expired after the eighth attempt", false);
            events.save(exhausted);
        }
        return events.lockDispatchable(now, limit).stream().map(event -> {
            String correlationId = "onboarding-outbox-" + UUID.randomUUID();
            event.reserve(workerId, correlationId, now);
            events.save(event);
            return new ReservedEvent(event.id(), event.aggregateId(), event.idempotencyKey(),
                    event.payloadJson(), correlationId);
        }).toList();
    }

    public record ReservedEvent(UUID eventId, UUID caseId, String idempotencyKey,
            String payloadJson, String correlationId) {}
}
