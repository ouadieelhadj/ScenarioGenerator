package com.staging.sg.card.issuing.domain;

import com.staging.sg.common.issuing.IssuingAuthorizationResponse;
import com.staging.sg.common.issuing.IssuingDecisionStatus;
import com.staging.sg.common.issuing.IssuingOperation;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "issuing_authorization", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issuing_auth_idempotency",
                columnNames = {"issuer_id", "caller_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_issuing_auth_transaction",
                columnNames = {"issuer_id", "caller_id", "transaction_id"})
})
public class IssuingAuthorization {
    @Id private UUID id;
    @Column(name="issuer_id",nullable=false,length=64,updatable=false) private String issuerId;
    @Column(name="caller_id",nullable=false,length=64,updatable=false) private String callerId;
    @Column(name="transaction_id",nullable=false,length=128,updatable=false) private String transactionId;
    @Column(name="correlation_id",nullable=false,length=128,updatable=false) private String correlationId;
    @Column(name="idempotency_key",nullable=false,length=128,updatable=false) private String idempotencyKey;
    @Column(name="request_fingerprint",nullable=false,length=64,updatable=false) private String requestFingerprint;
    @Column(name="payment_identifier_id",nullable=false,updatable=false) private UUID paymentIdentifierId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32,updatable=false) private IssuingOperation operation;
    @Column(name="original_transaction_id",length=128,updatable=false) private String originalTransactionId;
    @Column(name="amount_minor",nullable=false,updatable=false) private long amountMinor;
    @Column(name="approved_amount_minor",nullable=false) private long approvedAmountMinor;
    @Column(nullable=false,length=3,updatable=false) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private IssuingDecisionStatus status;
    @Column(name="internal_response_code",nullable=false,length=64) private String internalResponseCode;
    @Column(name="authorization_code",length=16) private String authorizationCode;
    @Column(nullable=false) private boolean retryable;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;

    protected IssuingAuthorization() {}

    public static IssuingAuthorization decided(
            String issuerId, String callerId, String transactionId,
            String correlationId, String idempotencyKey, String fingerprint,
            UUID paymentIdentifierId, IssuingOperation operation,
            String originalTransactionId, long amountMinor, String currency,
            IssuingDecisionStatus status, String responseCode,
            String authorizationCode, long approvedAmountMinor,
            boolean retryable) {
        if (blank(issuerId) || blank(callerId) || blank(transactionId)
                || blank(correlationId) || blank(idempotencyKey)
                || blank(fingerprint) || paymentIdentifierId == null
                || operation == null || amountMinor < 0 || currency == null
                || !currency.matches("\\d{3}") || status == null
                || blank(responseCode) || approvedAmountMinor < 0
                || approvedAmountMinor > amountMinor) {
            throw new IllegalArgumentException("Invalid issuer authorization");
        }
        IssuingAuthorization value = new IssuingAuthorization();
        value.id=UUID.randomUUID(); value.issuerId=issuerId; value.callerId=callerId;
        value.transactionId=transactionId; value.correlationId=correlationId;
        value.idempotencyKey=idempotencyKey; value.requestFingerprint=fingerprint;
        value.paymentIdentifierId=paymentIdentifierId; value.operation=operation;
        value.originalTransactionId=originalTransactionId; value.amountMinor=amountMinor;
        value.currency=currency; value.status=status;
        value.internalResponseCode=responseCode; value.authorizationCode=authorizationCode;
        value.approvedAmountMinor=approvedAmountMinor; value.retryable=retryable;
        value.createdAt=Instant.now(); value.updatedAt=value.createdAt;
        return value;
    }

    public boolean requestMatches(String fingerprint) { return requestFingerprint.equals(fingerprint); }
    public IssuingAuthorizationResponse response(boolean replayed) {
        return new IssuingAuthorizationResponse(
                "1.0", issuerId, transactionId, correlationId, status,
                internalResponseCode, authorizationCode, approvedAmountMinor,
                currency, null, retryable,
                Map.of("decisionOwner", "sg-card-issuing",
                        "replayed", Boolean.toString(replayed)));
    }
    public UUID id(){return id;} public String issuerId(){return issuerId;}
    public String transactionId(){return transactionId;}
    public UUID paymentIdentifierId(){return paymentIdentifierId;}
    public long amountMinor(){return amountMinor;} public String currency(){return currency;}
    public IssuingDecisionStatus status(){return status;}
    public String authorizationCode(){return authorizationCode;}
    public String internalResponseCode(){return internalResponseCode;}

    private static boolean blank(String value){return value==null||value.isBlank();}
}
