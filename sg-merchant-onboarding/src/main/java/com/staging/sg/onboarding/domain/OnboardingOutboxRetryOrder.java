package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_outbox_retry_order")
public class OnboardingOutboxRetryOrder {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false, updatable = false) private UUID eventId;
    @Column(name = "ordered_by", nullable = false, length = 96, updatable = false) private String orderedBy;
    @Column(nullable = false, length = 1000, updatable = false) private String reason;
    @Column(name = "previous_attempts", nullable = false, updatable = false) private int previousAttempts;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected OnboardingOutboxRetryOrder() {}

    public static OnboardingOutboxRetryOrder create(OnboardingOutboxEvent event,
            String actor, String reason) {
        if (actor == null || actor.isBlank() || reason == null || reason.isBlank())
            throw new IllegalArgumentException("Actor and reason are required");
        OnboardingOutboxRetryOrder value = new OnboardingOutboxRetryOrder();
        value.id = UUID.randomUUID();
        value.eventId = event.id();
        value.orderedBy = actor.trim();
        value.reason = reason.trim().length() <= 1000 ? reason.trim() : reason.trim().substring(0, 1000);
        value.previousAttempts = event.attempts();
        value.createdAt = Instant.now();
        return value;
    }

    public UUID id() { return id; }
}
