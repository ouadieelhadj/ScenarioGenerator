package com.staging.sg.softpos.contracts;

import java.time.Instant;
import java.util.Map;

/**
 * Versioned mobile contracts. Card credentials and PIN data are deliberately
 * absent: the backend receives only an opaque reference produced by the SDK.
 */
public final class SoftPosContracts {
    public static final String API_VERSION = "1.0";

    private SoftPosContracts() {
    }

    public enum AcceptanceChannel { NFC, QR_MPM, QR_CPM }
    public enum DeviceStatus { PENDING, ACTIVE, SUSPENDED, REVOKED, COMPROMISED }
    public enum TransactionStatus {
        RECEIVED, PROCESSING, APPROVED, DECLINED, UNKNOWN, REVERSAL_REQUIRED, REVERSED
    }
    public enum PosServerMode { REST_JSON, ISO8583_PERSISTENT }

    public record ActivationConsumeRequest(
            String activationCode, String devicePublicKey,
            String deviceFingerprint, String applicationVersion) {
    }

    public record ActivationResponse(
            String deviceId, String terminalId, DeviceStatus status,
            String sessionChallenge, Instant expiresAt) {
    }

    public record IntegrityVerdictRequest(
            String deviceId, String nonce, String verdictToken,
            Instant evaluatedAt) {
    }

    public record PaymentRequest(
            String clientTransactionId, String idempotencyKey,
            AcceptanceChannel acceptanceChannel, long amountMinor,
            String currency, String sdkCredentialReference,
            String integrityReference, Map<String, String> attributes) {
    }

    public record PaymentResponse(
            String clientTransactionId, TransactionStatus status,
            String responseCode, String authorizationCode,
            String receiptReference, boolean idempotentReplay,
            Instant updatedAt) {
    }

    public record QrRequest(
            String clientTransactionId, String idempotencyKey,
            AcceptanceChannel acceptanceChannel, long amountMinor,
            String currency, String qrReference, Instant expiresAt) {
    }

    public record RouteView(
            String memberId, String environment, PosServerMode primaryMode,
            String endpoint, int connectTimeoutMillis, int responseTimeoutMillis,
            boolean active) {
    }

    /** Private backend-to-POServer contract, never exposed to the mobile app. */
    public record PosServerPaymentCommand(
            String memberId, String posTransactionId, String terminalId,
            String merchantId, AcceptanceChannel acceptanceChannel,
            long amountMinor, String currency, String sdkCredentialReference) {
    }

    public record PosServerPaymentResult(
            TransactionStatus status, String responseCode,
            String authorizationCode) {
    }
}
