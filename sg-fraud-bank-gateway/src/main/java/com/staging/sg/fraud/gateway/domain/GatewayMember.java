package com.staging.sg.fraud.gateway.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fraud_gateway_member")
public class GatewayMember {
    @Id
    @Column(name = "member_id", length = 64, nullable = false)
    private String memberId;
    @Column(name = "display_name", length = 160, nullable = false)
    private String displayName;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GatewayMember() {}
    public GatewayMember(String memberId, String displayName, boolean active) {
        this.memberId = memberId; this.displayName = displayName; this.active = active; this.createdAt = Instant.now();
    }
    public String memberId() { return memberId; }
    public String displayName() { return displayName; }
    public boolean active() { return active; }
}
