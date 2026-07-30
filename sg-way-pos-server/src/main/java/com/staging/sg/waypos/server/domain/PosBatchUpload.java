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
@Table(name = "pos_batch_uploads", uniqueConstraints = @UniqueConstraint(
        name = "uk_pos_batch_upload",
        columnNames = {"terminal_id", "batch_id", "message_fingerprint"}))
public class PosBatchUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;
    @Column(name = "batch_id", nullable = false, length = 6)
    private String batchId;
    @Column(name = "message_fingerprint", nullable = false, length = 64)
    private String messageFingerprint;
    @Column(name = "original_mti", nullable = false, length = 4)
    private String originalMti;
    @Column(name = "processing_code", nullable = false, length = 6)
    private String processingCode;
    @Column(name = "network_id", length = 3)
    private String networkId;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "response_code", length = 2)
    private String responseCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PosBatchUpload() {}

    public static PosBatchUpload received(
            String terminalId, String batchId, String fingerprint,
            String originalMti, String processingCode, String networkId,
            long amount, String currency, String responseCode) {
        PosBatchUpload value = new PosBatchUpload();
        value.terminalId = terminalId;
        value.batchId = batchId;
        value.messageFingerprint = fingerprint;
        value.originalMti = originalMti;
        value.processingCode = processingCode;
        value.networkId = networkId;
        value.amountMinor = amount;
        value.currency = currency;
        value.responseCode = responseCode;
        value.createdAt = Instant.now();
        return value;
    }

    public String getOriginalMti() { return originalMti; }
    public String getProcessingCode() { return processingCode; }
    public String getNetworkId() { return networkId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getResponseCode() { return responseCode; }
}
