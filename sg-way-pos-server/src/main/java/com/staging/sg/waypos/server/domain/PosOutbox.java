package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "pos_outbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_pos_outbox_recovery",
        columnNames = {"transaction_id", "message_type", "destination"}))
public class PosOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;
    @Column(name = "message_type", nullable = false, length = 32)
    private String messageType;
    @Column(name = "destination", nullable = false, length = 32)
    private String destination;
    @Column(name = "payload_ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] payloadCiphertext;
    @Column(name = "payload_iv", nullable = false, columnDefinition = "bytea")
    private byte[] payloadIv;
    @Column(name = "payload_key_id", nullable = false, length = 16)
    private String payloadKeyId;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_response_code", length = 3)
    private String lastResponseCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected PosOutbox() {}

    public static PosOutbox pending(
            String transactionId, String messageType, String destination,
            byte[] ciphertext, byte[] iv, String keyId) {
        PosOutbox value = new PosOutbox();
        value.transactionId = transactionId;
        value.messageType = messageType;
        value.destination = destination;
        value.payloadCiphertext = ciphertext.clone();
        value.payloadIv = iv.clone();
        value.payloadKeyId = keyId;
        value.status = "PENDING";
        value.attempts = 0;
        value.nextAttemptAt = Instant.now().plusSeconds(10);
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void delivered(String responseCode) {
        status = "DELIVERED";
        lastResponseCode = responseCode;
        updatedAt = Instant.now();
    }

    public void retry() {
        attempts++;
        if (attempts >= 10) {
            status = "MANUAL";
        } else {
            long delay = Math.min(300L, 5L << Math.min(attempts, 6));
            nextAttemptAt = Instant.now().plusSeconds(delay);
            status = "PENDING";
        }
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDestination() { return destination; }
    public byte[] getPayloadCiphertext() { return payloadCiphertext.clone(); }
    public byte[] getPayloadIv() { return payloadIv.clone(); }
    public String getPayloadKeyId() { return payloadKeyId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getMessageType() { return messageType; }
}
