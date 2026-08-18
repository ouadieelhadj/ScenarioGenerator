package com.staging.sg.softpos.domain;

import com.staging.sg.softpos.contracts.SoftPosContracts.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "softpos_transaction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_softpos_tx_member_client", columnNames = {"member_id", "client_transaction_id"}),
        @UniqueConstraint(name = "uk_softpos_tx_member_idempotency", columnNames = {"member_id", "idempotency_key"})})
public class SoftPosTransaction {
    @Id @Column(name = "transaction_id", length = 36) private String transactionId;
    @Column(name = "member_id", nullable = false, length = 64) private String memberId;
    @Column(name = "device_id", nullable = false, length = 36) private String deviceId;
    @Column(name = "client_transaction_id", nullable = false, length = 64) private String clientTransactionId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(name = "acceptance_channel", nullable = false, length = 16) private AcceptanceChannel acceptanceChannel;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "credential_reference_hash", nullable = false, length = 64) private String credentialReferenceHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransactionStatus status;
    @Column(name = "response_code", length = 3) private String responseCode;
    @Column(name = "authorization_code", length = 12) private String authorizationCode;
    @Column(name = "pos_transaction_id", length = 64) private String posTransactionId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected SoftPosTransaction() {}
    public static SoftPosTransaction received(String memberId, String deviceId, PaymentRequest request, String credentialHash) {
        if (request == null || request.clientTransactionId() == null || request.idempotencyKey() == null
                || request.acceptanceChannel() == null || request.amountMinor() <= 0
                || request.currency() == null || !request.currency().matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid payment request");
        SoftPosTransaction t = new SoftPosTransaction(); t.transactionId = UUID.randomUUID().toString();
        t.memberId = memberId; t.deviceId = deviceId; t.clientTransactionId = request.clientTransactionId();
        t.idempotencyKey = request.idempotencyKey(); t.acceptanceChannel = request.acceptanceChannel();
        t.amountMinor = request.amountMinor(); t.currency = request.currency(); t.credentialReferenceHash = credentialHash;
        t.status = TransactionStatus.RECEIVED; t.createdAt = Instant.now(); t.updatedAt = t.createdAt; return t;
    }
    public void processing(String posId) { status = TransactionStatus.PROCESSING; posTransactionId = posId; updatedAt = Instant.now(); }
    public void complete(TransactionStatus status, String responseCode, String authorizationCode) { this.status = status; this.responseCode = responseCode; this.authorizationCode = authorizationCode; updatedAt = Instant.now(); }
    public void unknown() { status = TransactionStatus.UNKNOWN; updatedAt = Instant.now(); }
    public String getMemberId() { return memberId; } public String getDeviceId() { return deviceId; }
    public String getClientTransactionId() { return clientTransactionId; } public TransactionStatus getStatus() { return status; }
    public String getResponseCode() { return responseCode; } public String getAuthorizationCode() { return authorizationCode; }
    public Instant getUpdatedAt() { return updatedAt; } public String getTransactionId() { return transactionId; }
    public AcceptanceChannel getAcceptanceChannel() { return acceptanceChannel; } public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
}
