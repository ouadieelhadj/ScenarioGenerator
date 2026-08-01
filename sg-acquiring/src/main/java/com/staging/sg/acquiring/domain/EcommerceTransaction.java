package com.staging.sg.acquiring.domain;

import com.staging.sg.common.ecommerce.EcommerceAuthenticationStatus;
import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import com.staging.sg.common.ecommerce.EcommercePurchaseResponse;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acquiring_ecommerce_transaction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_acq_ecom_transaction_reference",
                columnNames = {"acquirer_id", "transaction_id"}),
        @UniqueConstraint(name = "uk_acq_ecom_idempotency",
                columnNames = {"acquirer_id", "idempotency_key"})
})
public class EcommerceTransaction {
    @Id
    private UUID id;
    @Column(name = "acquirer_id", nullable = false, length = 64, updatable = false)
    private String acquirerId;
    @Column(name = "transaction_id", nullable = false, length = 64, updatable = false)
    private String transactionId;
    @Column(name = "correlation_id", nullable = false, length = 128, updatable = false)
    private String correlationId;
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;
    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;
    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;
    @Column(name = "merchant_order_id", nullable = false, length = 128, updatable = false)
    private String merchantOrderId;
    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_identifier_type", nullable = false, length = 24, updatable = false)
    private PaymentIdentifierType paymentIdentifierType;
    @Enumerated(EnumType.STRING)
    @Column(name = "network_route", nullable = false, length = 32, updatable = false)
    private EcommerceNetworkRoute networkRoute;
    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_status", nullable = false, length = 24, updatable = false)
    private EcommerceAuthenticationStatus authenticationStatus;
    @Column(name = "network_stan", nullable = false, length = 6, updatable = false)
    private String networkStan;
    @Column(name = "network_rrn", nullable = false, length = 12, updatable = false)
    private String networkRrn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EcommerceTransactionStatus status;
    @Column(name = "response_code", length = 8)
    private String responseCode;
    @Column(name = "authorization_code", length = 12)
    private String authorizationCode;
    @Column(name = "approved_amount_minor", nullable = false)
    private long approvedAmountMinor;
    @Column(nullable = false)
    private boolean retryable;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected EcommerceTransaction() {}

    public static EcommerceTransaction received(String acquirerId,
            String transactionId, String correlationId, String idempotencyKey,
            String fingerprint, UUID profileId, UUID contractId,
            String merchantOrderId, long amountMinor, String currency,
            PaymentIdentifierType identifierType, EcommerceNetworkRoute route,
            EcommerceAuthenticationStatus authenticationStatus,
            String stan, String rrn) {
        EcommerceTransaction value = new EcommerceTransaction();
        value.id = UUID.randomUUID();
        value.acquirerId = acquirerId;
        value.transactionId = transactionId;
        value.correlationId = correlationId;
        value.idempotencyKey = idempotencyKey;
        value.requestFingerprint = fingerprint;
        value.profileId = profileId;
        value.contractId = contractId;
        value.merchantOrderId = merchantOrderId;
        value.amountMinor = amountMinor;
        value.currency = currency;
        value.paymentIdentifierType = identifierType;
        value.networkRoute = route;
        value.authenticationStatus = authenticationStatus;
        value.networkStan = stan;
        value.networkRrn = rrn;
        value.status = EcommerceTransactionStatus.RECEIVED;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void decide(RoutingTransactionResponse response) {
        status = switch (response.status()) {
            case "APPROVED", "PARTIALLY_APPROVED" -> EcommerceTransactionStatus.APPROVED;
            case "DECLINED" -> EcommerceTransactionStatus.DECLINED;
            default -> EcommerceTransactionStatus.UNKNOWN;
        };
        responseCode = response.posResponseCode();
        authorizationCode = response.authorizationCode();
        approvedAmountMinor = parseAmount(response.approvedAmount());
        retryable = response.retryable();
        updatedAt = Instant.now();
    }

    public void unknown(String code) {
        status = EcommerceTransactionStatus.UNKNOWN;
        responseCode = code;
        retryable = true;
        updatedAt = Instant.now();
    }

    public boolean matches(String fingerprint) { return requestFingerprint.equals(fingerprint); }
    public boolean canRetry() { return status == EcommerceTransactionStatus.UNKNOWN && retryable; }
    public UUID id() { return id; }
    public String transactionId() { return transactionId; }
    public String stan() { return networkStan; }
    public String rrn() { return networkRrn; }
    public EcommercePurchaseResponse response(boolean replayed) {
        return new EcommercePurchaseResponse("1.0", transactionId, status.name(),
                responseCode, authorizationCode, networkRoute, approvedAmountMinor,
                currency, authenticationStatus, retryable, replayed);
    }

    private static long parseAmount(String value) {
        return value != null && value.matches("\\d{1,12}") ? Long.parseLong(value) : 0L;
    }
}
