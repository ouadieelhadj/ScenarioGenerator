package com.staging.sg.card.issuing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "issuing_interface_endpoint",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_issuing_interface_version",
                        columnNames = {"issuer_id", "interface_type", "interface_version"}),
                @UniqueConstraint(name = "uk_issuing_interface_idempotency",
                        columnNames = {"issuer_id", "created_by", "creation_idempotency_key"})
        })
public class IssuingInterfaceEndpoint {
    @Id
    private UUID id;
    @Column(name = "issuer_id", nullable = false, length = 64, updatable = false)
    private String issuerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "interface_type", nullable = false, length = 32, updatable = false)
    private IssuingInterfaceType interfaceType;
    @Column(name = "interface_version", nullable = false, updatable = false)
    private int interfaceVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private IssuingInterfaceDirection direction;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private IssuingInterfaceProtocol protocol;
    @Column(nullable = false, length = 255, updatable = false)
    private String host;
    @Column(nullable = false, updatable = false)
    private int port;
    @Column(name = "base_path", length = 255, updatable = false)
    private String basePath;
    @Column(name = "connect_timeout_ms", nullable = false, updatable = false)
    private int connectTimeoutMs;
    @Column(name = "read_timeout_ms", nullable = false, updatable = false)
    private int readTimeoutMs;
    @Column(name = "tls_profile", length = 128, updatable = false)
    private String tlsProfile;
    @Column(name = "secret_reference", length = 255, updatable = false)
    private String secretReference;
    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT",
            updatable = false)
    private String parametersJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssuingInterfaceStatus status;
    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;
    @Column(name = "creation_idempotency_key", nullable = false, length = 128,
            updatable = false)
    private String creationIdempotencyKey;
    @Column(name = "creation_fingerprint", nullable = false, length = 64,
            updatable = false)
    private String creationFingerprint;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long rowVersion;

    protected IssuingInterfaceEndpoint() {
    }

    public static IssuingInterfaceEndpoint draft(
            String issuerId, IssuingInterfaceType interfaceType,
            int interfaceVersion, IssuingInterfaceDirection direction,
            IssuingInterfaceProtocol protocol, String host, int port,
            String basePath, int connectTimeoutMs, int readTimeoutMs,
            String tlsProfile, String secretReference, String parametersJson,
            String createdBy, String idempotencyKey, String fingerprint) {
        if (blank(issuerId) || interfaceType == null || interfaceVersion < 1
                || direction == null || protocol == null || blank(host)
                || port < 1 || port > 65535
                || connectTimeoutMs < 1 || readTimeoutMs < 1
                || blank(parametersJson) || blank(createdBy)
                || blank(idempotencyKey) || blank(fingerprint)) {
            throw new IllegalArgumentException("Invalid issuing interface endpoint");
        }
        if ((protocol == IssuingInterfaceProtocol.TLS_TCP
                || protocol == IssuingInterfaceProtocol.REST_TLS)
                && blank(tlsProfile)) {
            throw new IllegalArgumentException(
                    "TLS profile is required for a TLS protocol");
        }
        IssuingInterfaceEndpoint value = new IssuingInterfaceEndpoint();
        value.id = UUID.randomUUID();
        value.issuerId = issuerId;
        value.interfaceType = interfaceType;
        value.interfaceVersion = interfaceVersion;
        value.direction = direction;
        value.protocol = protocol;
        value.host = host;
        value.port = port;
        value.basePath = basePath;
        value.connectTimeoutMs = connectTimeoutMs;
        value.readTimeoutMs = readTimeoutMs;
        value.tlsProfile = tlsProfile;
        value.secretReference = secretReference;
        value.parametersJson = parametersJson;
        value.status = IssuingInterfaceStatus.DRAFT;
        value.createdBy = createdBy;
        value.creationIdempotencyKey = idempotencyKey;
        value.creationFingerprint = fingerprint;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public boolean approve(String approver) {
        if (status == IssuingInterfaceStatus.APPROVED) return false;
        if (status != IssuingInterfaceStatus.DRAFT) {
            throw new IllegalStateException("Only a draft interface can be approved");
        }
        if (createdBy.equals(approver)) {
            throw new IllegalStateException(
                    "Maker and checker must be different for interface approval");
        }
        status = IssuingInterfaceStatus.APPROVED;
        updatedAt = Instant.now();
        return true;
    }

    public boolean activate() {
        if (status == IssuingInterfaceStatus.ACTIVE) return false;
        if (status != IssuingInterfaceStatus.APPROVED) {
            throw new IllegalStateException("Only an approved interface can be activated");
        }
        status = IssuingInterfaceStatus.ACTIVE;
        updatedAt = Instant.now();
        return true;
    }

    public boolean disable() {
        if (status == IssuingInterfaceStatus.DISABLED) return false;
        if (status != IssuingInterfaceStatus.ACTIVE) {
            throw new IllegalStateException("Only an active interface can be disabled");
        }
        status = IssuingInterfaceStatus.DISABLED;
        updatedAt = Instant.now();
        return true;
    }

    public boolean creationMatches(String fingerprint) {
        return creationFingerprint.equals(fingerprint);
    }

    public UUID id() { return id; }
    public String issuerId() { return issuerId; }
    public IssuingInterfaceType interfaceType() { return interfaceType; }
    public int interfaceVersion() { return interfaceVersion; }
    public IssuingInterfaceDirection direction() { return direction; }
    public IssuingInterfaceProtocol protocol() { return protocol; }
    public String host() { return host; }
    public int port() { return port; }
    public String basePath() { return basePath; }
    public int connectTimeoutMs() { return connectTimeoutMs; }
    public int readTimeoutMs() { return readTimeoutMs; }
    public String tlsProfile() { return tlsProfile; }
    public String secretReference() { return secretReference; }
    public String parametersJson() { return parametersJson; }
    public IssuingInterfaceStatus status() { return status; }
    public String createdBy() { return createdBy; }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
