package com.staging.sg.acquiring.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acquiring_outbox_event")
public class AcquiringOutboxEvent {
    @Id
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 96)
    private String eventType;
    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AcquiringOutboxEvent() {}

    public static AcquiringOutboxEvent pending(String aggregateType, UUID aggregateId,
            String eventType, String correlationId, String payloadJson) {
        if (AcceptanceProduct.blank(aggregateType) || aggregateId == null
                || AcceptanceProduct.blank(eventType)
                || AcceptanceProduct.blank(correlationId)
                || AcceptanceProduct.blank(payloadJson)) {
            throw new IllegalArgumentException("Invalid acquiring outbox event");
        }
        AcquiringOutboxEvent value = new AcquiringOutboxEvent();
        value.id = UUID.randomUUID();
        value.aggregateType = aggregateType;
        value.aggregateId = aggregateId.toString();
        value.eventType = eventType;
        value.correlationId = correlationId;
        value.payloadJson = payloadJson;
        value.status = "PENDING";
        value.createdAt = Instant.now();
        return value;
    }
}
