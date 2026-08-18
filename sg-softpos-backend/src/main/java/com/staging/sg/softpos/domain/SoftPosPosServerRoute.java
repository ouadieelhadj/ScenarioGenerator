package com.staging.sg.softpos.domain;

import com.staging.sg.softpos.contracts.SoftPosContracts.PosServerMode;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "softpos_poserver_route", uniqueConstraints = @UniqueConstraint(name = "uk_softpos_route_member_env", columnNames = {"member_id", "environment"}))
public class SoftPosPosServerRoute {
    @Id @Column(name = "route_id", length = 36) private String routeId;
    @Column(name = "member_id", nullable = false, length = 64) private String memberId;
    @Column(nullable = false, length = 32) private String environment;
    @Enumerated(EnumType.STRING) @Column(name = "primary_mode", nullable = false, length = 24) private PosServerMode primaryMode;
    @Column(nullable = false, length = 256) private String endpoint;
    @Column(name = "connect_timeout_ms", nullable = false) private int connectTimeoutMillis;
    @Column(name = "response_timeout_ms", nullable = false) private int responseTimeoutMillis;
    @Column(nullable = false) private boolean active;
    @Version private long version;
    protected SoftPosPosServerRoute() {}
    public static SoftPosPosServerRoute configured(String memberId, String environment, PosServerMode mode,
            String endpoint, int connectTimeoutMillis, int responseTimeoutMillis, boolean active) {
        if (memberId == null || memberId.isBlank() || environment == null || environment.isBlank() || mode == null
                || endpoint == null || endpoint.isBlank() || connectTimeoutMillis < 100 || responseTimeoutMillis < 100) {
            throw new IllegalArgumentException("Invalid POServer route");
        }
        SoftPosPosServerRoute r = new SoftPosPosServerRoute(); r.routeId = UUID.randomUUID().toString();
        r.memberId = memberId; r.environment = environment; r.primaryMode = mode; r.endpoint = endpoint;
        r.connectTimeoutMillis = connectTimeoutMillis; r.responseTimeoutMillis = responseTimeoutMillis; r.active = active; return r;
    }
    public void update(PosServerMode mode, String endpoint, int connect, int response, boolean active) {
        SoftPosPosServerRoute candidate = configured(memberId, environment, mode, endpoint, connect, response, active);
        primaryMode = candidate.primaryMode; this.endpoint = candidate.endpoint; connectTimeoutMillis = connect;
        responseTimeoutMillis = response; this.active = active;
    }
    public String getMemberId() { return memberId; } public String getEnvironment() { return environment; }
    public PosServerMode getPrimaryMode() { return primaryMode; } public String getEndpoint() { return endpoint; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; } public int getResponseTimeoutMillis() { return responseTimeoutMillis; }
    public boolean isActive() { return active; }
}
