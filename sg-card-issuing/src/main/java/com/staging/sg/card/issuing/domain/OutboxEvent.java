package com.staging.sg.card.issuing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuing_outbox_event")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, length = 64, updatable = false)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 96, updatable = false)
    private String eventType;
    @Column(name = "correlation_id", nullable = false, length = 128, updatable = false)
    private String correlationId;
    @Column(name = "payload_json", nullable = false, columnDefinition = "text",
            updatable = false)
    private String payloadJson;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(
            String aggregateType, String aggregateId, String eventType,
            String correlationId, String payloadJson) {
        if (blank(aggregateType) || blank(aggregateId) || blank(eventType)
                || blank(correlationId) || blank(payloadJson)) {
            throw new IllegalArgumentException("Invalid outbox event");
        }
        OutboxEvent value = new OutboxEvent();
        value.id = UUID.randomUUID();
        value.aggregateType = aggregateType;
        value.aggregateId = aggregateId;
        value.eventType = eventType;
        value.correlationId = correlationId;
        value.payloadJson = payloadJson;
        value.status = "PENDING";
        value.createdAt = Instant.now();
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
