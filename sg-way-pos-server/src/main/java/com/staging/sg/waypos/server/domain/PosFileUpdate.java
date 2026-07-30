package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "pos_file_updates", uniqueConstraints = @UniqueConstraint(
        name = "uk_pos_file_update_fingerprint",
        columnNames = {"terminal_id", "message_fingerprint"}))
public class PosFileUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;
    @Column(name = "message_fingerprint", nullable = false, length = 64)
    private String messageFingerprint;
    @Column(name = "request_data", nullable = false, columnDefinition = "bytea")
    private byte[] requestData;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PosFileUpdate() {}

    public static PosFileUpdate received(
            String terminalId, String fingerprint, byte[] requestData) {
        PosFileUpdate value = new PosFileUpdate();
        value.terminalId = terminalId;
        value.messageFingerprint = fingerprint;
        value.requestData = requestData.clone();
        value.status = "RECEIVED";
        value.createdAt = Instant.now();
        return value;
    }
}
