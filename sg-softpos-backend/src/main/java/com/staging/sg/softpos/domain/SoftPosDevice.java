package com.staging.sg.softpos.domain;

import com.staging.sg.softpos.contracts.SoftPosContracts.DeviceStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "softpos_device", uniqueConstraints = {
        @UniqueConstraint(name = "uk_softpos_device_member_fingerprint", columnNames = {"member_id", "fingerprint_hash"}),
        @UniqueConstraint(name = "uk_softpos_device_member_terminal", columnNames = {"member_id", "terminal_id"})})
public class SoftPosDevice {
    @Id @Column(name = "device_id", length = 36) private String deviceId;
    @Column(name = "member_id", nullable = false, length = 64) private String memberId;
    @Column(name = "merchant_id", nullable = false, length = 64) private String merchantId;
    @Column(name = "outlet_id", nullable = false, length = 64) private String outletId;
    @Column(name = "terminal_id", nullable = false, length = 8) private String terminalId;
    @Column(name = "fingerprint_hash", nullable = false, length = 64) private String fingerprintHash;
    @Column(name = "public_key_hash", nullable = false, length = 64) private String publicKeyHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private DeviceStatus status;
    @Column(name = "application_version", nullable = false, length = 32) private String applicationVersion;
    @Column(name = "integrity_valid_until") private Instant integrityValidUntil;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;

    protected SoftPosDevice() {}

    public static SoftPosDevice activate(String memberId, String merchantId, String outletId,
            String terminalId, String fingerprintHash, String publicKeyHash, String applicationVersion) {
        SoftPosDevice value = new SoftPosDevice();
        value.deviceId = UUID.randomUUID().toString();
        value.memberId = required(memberId); value.merchantId = required(merchantId);
        value.outletId = required(outletId); value.terminalId = required(terminalId);
        value.fingerprintHash = required(fingerprintHash); value.publicKeyHash = required(publicKeyHash);
        value.applicationVersion = required(applicationVersion);
        value.status = DeviceStatus.PENDING; value.createdAt = Instant.now();
        return value;
    }

    public void attest(Instant validUntil) { integrityValidUntil = validUntil; status = DeviceStatus.ACTIVE; }
    public void changeStatus(DeviceStatus next) { status = next; }
    public boolean mayTransact(Instant now) { return status == DeviceStatus.ACTIVE && integrityValidUntil != null && integrityValidUntil.isAfter(now); }
    public String getDeviceId() { return deviceId; } public String getMemberId() { return memberId; }
    public String getMerchantId() { return merchantId; } public String getOutletId() { return outletId; }
    public String getTerminalId() { return terminalId; } public DeviceStatus getStatus() { return status; }
    public String getApplicationVersion() { return applicationVersion; }
    private static String required(String v) { if (v == null || v.isBlank()) throw new IllegalArgumentException("Required device field"); return v; }
}
