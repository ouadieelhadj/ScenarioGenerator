package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pos_terminal_profiles")
public class PosTerminalProfile {
    @Id
    @Column(name = "terminal_id", length = 8)
    private String terminalId;
    @Column(name = "merchant_id", nullable = false, length = 15)
    private String merchantId;
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;
    @Column(name = "outlet_id", nullable = false, length = 64)
    private String outletId;
    @Column(name = "terminal_type", nullable = false, length = 16)
    private String terminalType;
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    @Column(name = "extended_set", nullable = false)
    private boolean extendedSet;
    @Column(name = "mac_data", nullable = false, length = 3)
    private String macData;
    @Column(name = "mac_required", nullable = false)
    private boolean macRequired;
    @Column(name = "batch_id", nullable = false, length = 6)
    private String batchId;
    @Column(name = "batch_status", nullable = false, length = 24)
    private String batchStatus;
    @Column(name = "tak_under_lmk")
    private String takUnderLmk;
    @Column(name = "tak_kcv", length = 6)
    private String takKcv;
    @Column(name = "tak_length")
    private Integer takLength;
    @Column(name = "tpk_under_lmk")
    private String tpkUnderLmk;
    @Column(name = "tpk_kcv", length = 6)
    private String tpkKcv;
    @Column(name = "tpk_length")
    private Integer tpkLength;
    @Version
    private long version;

    protected PosTerminalProfile() {}

    public static PosTerminalProfile provisioned(
            String terminalId, String merchantId, boolean extendedSet,
            String macData, boolean macRequired, String batchId) {
        if (terminalId == null || !terminalId.matches("[A-Za-z0-9]{8}")
                || merchantId == null || !merchantId.matches("[A-Za-z0-9]{15}")
                || !("BIN".equals(macData) || "HEX".equals(macData))
                || batchId == null || !batchId.matches("\\d{6}")) {
            throw new IllegalArgumentException("Invalid terminal profile");
        }
        PosTerminalProfile value = new PosTerminalProfile();
        value.terminalId = terminalId;
        value.merchantId = merchantId;
        value.memberId = "DEFAULT";
        value.outletId = "DEFAULT";
        value.terminalType = "PHYSICAL_POS";
        value.enabled = true;
        value.extendedSet = extendedSet;
        value.macData = macData;
        value.macRequired = macRequired;
        value.batchId = batchId;
        value.batchStatus = "OPEN";
        return value;
    }

    public static PosTerminalProfile provisionedSoftPos(
            String terminalId, String merchantId, String memberId,
            String outletId, boolean extendedSet, String macData,
            boolean macRequired, String batchId) {
        PosTerminalProfile value = provisioned(terminalId, merchantId,
                extendedSet, macData, macRequired, batchId);
        if (memberId == null || memberId.isBlank()
                || outletId == null || outletId.isBlank()) {
            throw new IllegalArgumentException("Invalid SoftPOS ownership");
        }
        value.memberId = memberId;
        value.outletId = outletId;
        value.terminalType = "SOFTPOS";
        return value;
    }

    public String getTerminalId() { return terminalId; }
    public String getMerchantId() { return merchantId; }
    public String getMemberId() { return memberId; }
    public String getOutletId() { return outletId; }
    public String getTerminalType() { return terminalType; }
    public boolean isEnabled() { return enabled; }
    public boolean isExtendedSet() { return extendedSet; }
    public String getBatchId() { return batchId; }
    public String getMacData() { return macData; }
    public boolean isMacRequired() { return macRequired; }
    public String getTakUnderLmk() { return takUnderLmk; }
    public String getTakKcv() { return takKcv; }
    public Integer getTakLength() { return takLength; }
    public String getTpkUnderLmk() { return tpkUnderLmk; }
    public String getTpkKcv() { return tpkKcv; }
    public Integer getTpkLength() { return tpkLength; }
    public String getBatchStatus() { return batchStatus; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void activateWorkingKey(
            String keyType, String underLmk, String kcv, Integer length) {
        if (!("TAK".equals(keyType) || "TPK".equals(keyType))
                || underLmk == null || !underLmk.matches("(?i)[0-9a-f]+")
                || (underLmk.length() & 1) != 0
                || kcv == null || !kcv.matches("(?i)[0-9a-f]{6}")
                || length == null || !(length == 8 || length == 16)) {
            throw new IllegalArgumentException("Invalid terminal working-key metadata");
        }
        if ("TAK".equals(keyType)) {
            takUnderLmk = underLmk.toUpperCase();
            takKcv = kcv.toUpperCase();
            takLength = length;
        } else {
            tpkUnderLmk = underLmk.toUpperCase();
            tpkKcv = kcv.toUpperCase();
            tpkLength = length;
        }
    }

    public void nextBatch() {
        long value = Long.parseLong(batchId);
        batchId = "%06d".formatted((value + 1) % 1_000_000L);
        batchStatus = "OPEN";
    }

    public boolean acceptsFinancialTransactions() {
        return "OPEN".equals(batchStatus);
    }

    public void requireBatchUpload() {
        batchStatus = "BATCH_UPLOAD_REQUIRED";
    }

    public void requireManualReconciliation() {
        batchStatus = "MANUAL_REQUIRED";
    }
}
