package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_event_route", uniqueConstraints = @UniqueConstraint(
        name = "uk_fraud_event_route_business_key",
        columnNames = {"member_id", "sector_id", "event_type"}))
public class FraudEventRoute {
    @Id private UUID id;
    @Column(name = "member_id", nullable = false, length = 64, updatable = false) private String memberId;
    @Column(name = "sector_id", nullable = false, length = 64, updatable = false) private String sectorId;
    @Column(name = "event_type", nullable = false, length = 64, updatable = false) private String eventType;
    @Column(name = "topic_template", nullable = false, length = 249) private String topicTemplate;
    @Column(name = "schema_version", nullable = false, length = 32) private String schemaVersion;
    @Column(name = "retention_class", nullable = false, length = 32) private String retentionClass;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private int priority;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected FraudEventRoute() {}

    public static FraudEventRoute create(String memberId, String sectorId, String eventType,
            String topicTemplate, String schemaVersion, String retentionClass, boolean enabled, int priority) {
        FraudEventRoute route = new FraudEventRoute();
        route.id = UUID.randomUUID();
        route.memberId = memberId;
        route.sectorId = sectorId;
        route.eventType = eventType;
        route.createdAt = Instant.now();
        route.update(topicTemplate, schemaVersion, retentionClass, enabled, priority);
        return route;
    }

    public void update(String topicTemplate, String schemaVersion, String retentionClass, boolean enabled, int priority) {
        this.topicTemplate = topicTemplate;
        this.schemaVersion = schemaVersion;
        this.retentionClass = retentionClass;
        this.enabled = enabled;
        this.priority = priority;
        this.updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public String memberId() { return memberId; }
    public String sectorId() { return sectorId; }
    public String eventType() { return eventType; }
    public String topicTemplate() { return topicTemplate; }
    public String schemaVersion() { return schemaVersion; }
    public String retentionClass() { return retentionClass; }
    public boolean enabled() { return enabled; }
    public int priority() { return priority; }
    public Instant updatedAt() { return updatedAt; }
}
