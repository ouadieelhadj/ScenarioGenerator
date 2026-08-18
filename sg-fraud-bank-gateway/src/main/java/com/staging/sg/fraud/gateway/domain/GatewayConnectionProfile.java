package com.staging.sg.fraud.gateway.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_gateway_connection_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_gateway_connection_code", columnNames = "connection_code"),
                @UniqueConstraint(name = "uk_gateway_protocol_listen_port", columnNames = {"protocol", "listen_port"})
        }, indexes = {
                @Index(name = "ix_gateway_connection_member_sector", columnList = "member_id,sector_id"),
                @Index(name = "ix_gateway_connection_active", columnList = "active,protocol")
        })
public class GatewayConnectionProfile {
    @Id private UUID id;
    @Column(name = "connection_code", length = 96, nullable = false) private String connectionCode;
    @Column(name = "member_id", length = 64, nullable = false) private String memberId;
    @Column(name = "sector_id", length = 64) private String sectorId;
    @Column(length = 16, nullable = false) private String protocol;
    @Column(name = "connection_mode", length = 16, nullable = false) private String connectionMode;
    @Column(name = "listen_port", nullable = false) private int listenPort;
    @Column(name = "remote_host", length = 253) private String remoteHost;
    @Column(name = "remote_port") private Integer remotePort;
    @Column(name = "message_profile", length = 128) private String messageProfile;
    @Column(name = "credential_reference", length = 160) private String credentialReference;
    @Column(name = "zmk_reference", length = 160) private String zmkReference;
    @Column(name = "echo_interval_seconds", nullable = false) private int echoIntervalSeconds;
    @Column(name = "reconnect_backoff_seconds", nullable = false) private int reconnectBackoffSeconds;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected GatewayConnectionProfile() {}
    public GatewayConnectionProfile(String connectionCode, String memberId, String sectorId,
            String protocol, String connectionMode, int listenPort, String remoteHost,
            Integer remotePort, String messageProfile, String credentialReference,
            String zmkReference, int echoIntervalSeconds, int reconnectBackoffSeconds, boolean active) {
        this.id = UUID.randomUUID(); this.connectionCode = connectionCode; this.memberId = memberId;
        this.sectorId = sectorId; this.protocol = protocol; this.connectionMode = connectionMode;
        this.listenPort = listenPort; this.remoteHost = remoteHost; this.remotePort = remotePort;
        this.messageProfile = messageProfile; this.credentialReference = credentialReference;
        this.zmkReference = zmkReference; this.echoIntervalSeconds = echoIntervalSeconds;
        this.reconnectBackoffSeconds = reconnectBackoffSeconds; this.active = active;
        this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID id() { return id; }
    public String connectionCode() { return connectionCode; }
    public String memberId() { return memberId; }
    public String sectorId() { return sectorId; }
    public String protocol() { return protocol; }
    public String connectionMode() { return connectionMode; }
    public int listenPort() { return listenPort; }
    public String remoteHost() { return remoteHost; }
    public Integer remotePort() { return remotePort; }
    public String messageProfile() { return messageProfile; }
    public String credentialReference() { return credentialReference; }
    public String zmkReference() { return zmkReference; }
    public int echoIntervalSeconds() { return echoIntervalSeconds; }
    public int reconnectBackoffSeconds() { return reconnectBackoffSeconds; }
    public boolean active() { return active; }
}
