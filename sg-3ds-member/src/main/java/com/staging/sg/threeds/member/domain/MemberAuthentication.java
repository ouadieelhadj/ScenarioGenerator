package com.staging.sg.threeds.member.domain;

import com.staging.sg.common.threeds.*;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "three_ds_member_authentication")
public class MemberAuthentication {
    @Id
    @Column(name = "three_ds_server_trans_id")
    private UUID id;
    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;
    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;
    @Column(name = "ds_trans_id")
    private UUID dsTransId;
    @Column(name = "acs_trans_id")
    private UUID acsTransId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ThreeDsProgram program;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ThreeDsFlow flow;
    @Enumerated(EnumType.STRING)
    @Column(name = "issuer_mode", nullable = false, length = 32)
    private ThreeDsIssuerMode issuerMode;
    @Enumerated(EnumType.STRING)
    @Column(name = "trans_status", nullable = false, length = 1)
    private ThreeDsTransStatus transStatus;
    @Column(name = "pan_fingerprint", nullable = false, length = 64)
    private String panFingerprint;
    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;
    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(length = 2)
    private String eci;
    @Column(name = "evidence_fingerprint", length = 64)
    private String evidenceFingerprint;
    @Column(name = "evidence_consumed_at")
    private Instant evidenceConsumedAt;
    @Column(name = "challenge_attempts", nullable = false)
    private int challengeAttempts;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected MemberAuthentication() {}

    public static MemberAuthentication create(ThreeDsStartRequest request,
            UUID serverId, String panFingerprint) {
        MemberAuthentication value = new MemberAuthentication();
        value.id = serverId;
        value.transactionId = request.transactionId();
        value.correlationId = request.correlationId();
        value.program = request.program();
        value.flow = request.flow();
        value.issuerMode = request.issuerMode();
        value.transStatus = ThreeDsTransStatus.I;
        value.panFingerprint = panFingerprint;
        value.merchantId = request.merchantId();
        value.amountMinor = request.amountMinor();
        value.currency = request.currency();
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public static MemberAuthentication create(ThreeDsAReq request,
            String panFingerprint) {
        MemberAuthentication value = new MemberAuthentication();
        value.id = request.threeDSServerTransId();
        value.transactionId = request.transactionId();
        value.correlationId = request.correlationId();
        value.program = request.program();
        value.flow = request.flow();
        value.issuerMode = ThreeDsIssuerMode.MEMBER;
        value.transStatus = ThreeDsTransStatus.I;
        value.panFingerprint = panFingerprint;
        value.merchantId = request.merchantId();
        value.amountMinor = request.amountMinor();
        value.currency = request.currency();
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void apply(ThreeDsARes response, String evidenceFingerprint) {
        require(response.threeDSServerTransId(), response.program());
        dsTransId = response.dsTransId();
        acsTransId = response.acsTransId();
        transStatus = response.transStatus();
        eci = response.eci();
        this.evidenceFingerprint = evidenceFingerprint;
        updatedAt = Instant.now();
    }

    public void apply(ThreeDsRReq result, String evidenceFingerprint) {
        require(result.threeDSServerTransId(), result.program());
        if (dsTransId != null && !dsTransId.equals(result.dsTransId())) {
            throw new IllegalStateException("3DS Directory Server identifier mismatch");
        }
        if (acsTransId != null && !acsTransId.equals(result.acsTransId())) {
            throw new IllegalStateException("3DS ACS identifier mismatch");
        }
        dsTransId = result.dsTransId();
        acsTransId = result.acsTransId();
        transStatus = result.transStatus();
        eci = result.eci();
        this.evidenceFingerprint = evidenceFingerprint;
        updatedAt = Instant.now();
    }

    public void registerChallengeAttempt() { challengeAttempts++; updatedAt = Instant.now(); }
    public UUID id() { return id; }
    public String transactionId() { return transactionId; }
    public UUID dsTransId() { return dsTransId; }
    public UUID acsTransId() { return acsTransId; }
    public ThreeDsProgram program() { return program; }
    public ThreeDsFlow flow() { return flow; }
    public ThreeDsIssuerMode issuerMode() { return issuerMode; }
    public ThreeDsTransStatus transStatus() { return transStatus; }
    public String merchantId() { return merchantId; }
    public long amountMinor() { return amountMinor; }
    public String currency() { return currency; }
    public String eci() { return eci; }
    public int challengeAttempts() { return challengeAttempts; }
    public String evidenceFingerprint() { return evidenceFingerprint; }
    public Instant evidenceConsumedAt() { return evidenceConsumedAt; }

    public boolean matchesVerification(ThreeDsVerificationRequest request,
            String candidateFingerprint) {
        return transStatus == ThreeDsTransStatus.Y
                && transactionId.equals(request.transactionId())
                && dsTransId != null && dsTransId.equals(request.dsTransId())
                && program == request.program()
                && eci != null && eci.equals(request.eci())
                && evidenceFingerprint != null
                && evidenceFingerprint.equals(candidateFingerprint)
                && merchantId.equals(request.merchantReference())
                && amountMinor == request.amountMinor()
                && currency.equals(request.currency());
    }

    public void consumeEvidence() {
        if (evidenceConsumedAt == null) evidenceConsumedAt = Instant.now();
        updatedAt = Instant.now();
    }

    private void require(UUID serverId, ThreeDsProgram expectedProgram) {
        if (!id.equals(serverId) || program != expectedProgram) {
            throw new IllegalStateException("3DS result correlation mismatch");
        }
    }
}
