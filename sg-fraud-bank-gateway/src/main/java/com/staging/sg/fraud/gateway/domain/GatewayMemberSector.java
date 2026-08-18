package com.staging.sg.fraud.gateway.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_gateway_member_sector",
        uniqueConstraints = @UniqueConstraint(name = "uk_gateway_member_sector", columnNames = {"member_id", "sector_id"}))
public class GatewayMemberSector {
    @Id private UUID id;
    @Column(name = "member_id", length = 64, nullable = false) private String memberId;
    @Column(name = "sector_id", length = 64, nullable = false) private String sectorId;
    @Column(name = "display_name", length = 160, nullable = false) private String displayName;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected GatewayMemberSector() {}
    public GatewayMemberSector(String memberId, String sectorId, String displayName, boolean active) {
        this.id = UUID.randomUUID(); this.memberId = memberId; this.sectorId = sectorId;
        this.displayName = displayName; this.active = active; this.createdAt = Instant.now();
    }
    public String memberId() { return memberId; }
    public String sectorId() { return sectorId; }
    public String displayName() { return displayName; }
    public boolean active() { return active; }
}
