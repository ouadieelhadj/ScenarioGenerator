package com.staging.sg.way4aura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "way4_application_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_way4_application_source", columnNames = {"source_type","source_id"}),
        @UniqueConstraint(name = "uk_way4_application_reg_number", columnNames = "reg_number")})
public class Way4ApplicationState {
    @Id private UUID id;
    @Column(name = "source_type", nullable = false, length = 32, updatable = false) private String sourceType;
    @Column(name = "source_id", nullable = false, updatable = false) private UUID sourceId;
    @Column(name = "reg_number", nullable = false, length = 96, updatable = false) private String regNumber;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private Way4ApplicationStatus status;
    @Column(name = "way4_reference", length = 160) private String way4Reference;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected Way4ApplicationState() {}
    public static Way4ApplicationState pending(String sourceType, UUID sourceId, String regNumber, String hash) {
        Way4ApplicationState value = new Way4ApplicationState(); value.id = UUID.randomUUID();
        value.sourceType = sourceType; value.sourceId = sourceId;
        value.regNumber = regNumber;
        value.payloadHash = hash; value.status = Way4ApplicationStatus.PENDING;
        value.createdAt = Instant.now(); value.updatedAt = value.createdAt; return value;
    }
    public void generated() { status = Way4ApplicationStatus.GENERATED; updatedAt = Instant.now(); }
    public void recycleRejected(String correctedPayloadHash) {
        if (status != Way4ApplicationStatus.WAY4_REJECTED_RETRYABLE
                && status != Way4ApplicationStatus.WAY4_REJECTED_FINAL)
            throw new IllegalStateException("Only a rejected WAY4 application can be recycled");
        if (correctedPayloadHash == null || correctedPayloadHash.isBlank())
            throw new IllegalArgumentException("Corrected payload hash is required");
        payloadHash = correctedPayloadHash; status = Way4ApplicationStatus.PENDING; updatedAt = Instant.now();
    }
    public String payloadHash() { return payloadHash; } public Way4ApplicationStatus status() { return status; }
}
