package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pos_authorizations")
public class PosAuthorization {
    @Id
    @Column(name = "transaction_id", length = 64)
    private String transactionId;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;
    @Column(name = "mti", nullable = false, length = 4)
    private String mti;
    @Column(name = "processing_code", length = 6)
    private String processingCode;
    @Column(name = "pan_masked", length = 32)
    private String panMasked;
    @Column(name = "pan_hash", length = 64)
    private String panHash;
    @Column(name = "amount_minor")
    private Long amountMinor;
    @Column(name = "currency", length = 3)
    private String currency;
    @Column(name = "stan", length = 6)
    private String stan;
    @Column(name = "rrn", length = 12)
    private String rrn;
    @Column(name = "terminal_id", length = 8)
    private String terminalId;
    @Column(name = "merchant_id", length = 15)
    private String merchantId;
    @Column(name = "batch_id", length = 6)
    private String batchId;
    @Column(name = "network_id", length = 3)
    private String networkId;
    @Column(name = "operation_name", length = 64)
    private String operationName;
    @Column(name = "route_code", length = 32)
    private String routeCode;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "response_code", length = 3)
    private String responseCode;
    @Column(name = "authorization_code", length = 6)
    private String authorizationCode;
    @Column(name = "arpc_hex", length = 510)
    private String arpcHex;
    @Column(name = "original_transaction_id", length = 64)
    private String originalTransactionId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PosAuthorization() {
    }

    public static PosAuthorization received(
            String transactionId, String idempotencyKey, String mti,
            String processingCode, String panMasked, String panHash,
            Long amountMinor, String currency, String stan, String rrn,
            String terminalId, String merchantId, String batchId,
            String networkId, String operationName,
            String originalTransactionId) {
        PosAuthorization value = new PosAuthorization();
        value.transactionId = transactionId;
        value.idempotencyKey = idempotencyKey;
        value.mti = mti;
        value.processingCode = processingCode;
        value.panMasked = panMasked;
        value.panHash = panHash;
        value.amountMinor = amountMinor;
        value.currency = currency;
        value.stan = stan;
        value.rrn = rrn;
        value.terminalId = terminalId;
        value.merchantId = merchantId;
        value.batchId = batchId;
        value.networkId = networkId;
        value.operationName = operationName;
        value.originalTransactionId = originalTransactionId;
        value.status = "RECEIVED";
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void complete(String route, String status, String responseCode, String authCode) {
        complete(route, status, responseCode, authCode, null);
    }

    public void complete(
            String route, String status, String responseCode,
            String authCode, String arpcHex) {
        this.routeCode = route;
        this.status = status;
        this.responseCode = responseCode;
        this.authorizationCode = authCode;
        this.arpcHex = arpcHex;
        this.updatedAt = Instant.now();
    }

    public void markReversed() {
        this.status = "REVERSED";
        this.updatedAt = Instant.now();
    }

    public void markAutomaticallyReversed() {
        this.status = "AUTO_REVERSED";
        this.updatedAt = Instant.now();
    }

    public void adjustAmount(long newAmount) {
        if (newAmount < 0 || amountMinor == null || newAmount < amountMinor) {
            throw new IllegalArgumentException("Invalid adjusted amount");
        }
        amountMinor = newAmount;
        updatedAt = Instant.now();
    }

    public String getTransactionId() { return transactionId; }
    public String getStatus() { return status; }
    public String getResponseCode() { return responseCode; }
    public String getAuthorizationCode() { return authorizationCode; }
    public String getArpcHex() { return arpcHex; }
    public String getRouteCode() { return routeCode; }
    public Long getAmountMinor() { return amountMinor; }
    public String getPanHash() { return panHash; }
    public String getMti() { return mti; }
    public String getRrn() { return rrn; }
    public String getTerminalId() { return terminalId; }
    public String getBatchId() { return batchId; }
    public String getCurrency() { return currency; }
    public String getProcessingCode() { return processingCode; }
    public String getNetworkId() { return networkId; }
    public String getOperationName() { return operationName; }
}
