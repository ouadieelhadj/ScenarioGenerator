package com.staging.sg.softpos.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "softpos_activation", uniqueConstraints = @UniqueConstraint(name = "uk_softpos_activation_hash", columnNames = "activation_hash"))
public class SoftPosActivation {
    @Id @Column(name = "activation_id", length = 36) private String activationId;
    @Column(name = "activation_hash", nullable = false, length = 64) private String activationHash;
    @Column(name = "member_id", nullable = false, length = 64) private String memberId;
    @Column(name = "merchant_id", nullable = false, length = 64) private String merchantId;
    @Column(name = "outlet_id", nullable = false, length = 64) private String outletId;
    @Column(name = "terminal_id", nullable = false, length = 8) private String terminalId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    protected SoftPosActivation() {}

    public static SoftPosActivation issue(String hash, String memberId, String merchantId,
            String outletId, String terminalId, Instant expiresAt) {
        SoftPosActivation a = new SoftPosActivation(); a.activationId = UUID.randomUUID().toString();
        a.activationHash = hash; a.memberId = memberId; a.merchantId = merchantId;
        a.outletId = outletId; a.terminalId = terminalId; a.expiresAt = expiresAt; return a;
    }
    public void consume(Instant now) { if (consumedAt != null || !expiresAt.isAfter(now)) throw new IllegalStateException("Activation unavailable"); consumedAt = now; }
    public String getMemberId() { return memberId; } public String getMerchantId() { return merchantId; }
    public String getOutletId() { return outletId; } public String getTerminalId() { return terminalId; }
}
