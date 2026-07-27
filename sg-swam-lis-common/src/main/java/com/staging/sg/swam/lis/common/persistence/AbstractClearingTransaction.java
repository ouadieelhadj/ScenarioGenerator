package com.staging.sg.swam.lis.common.persistence;

import com.staging.sg.swam.lis.common.model.*;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AbstractClearingTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "bank_member_id", nullable = false, length = 20)
    private String bankMemberId;
    @Column(name = "business_day_id", nullable = false)
    private Long businessDayId;
    @Column(name = "local_sid_transaction_id")
    private Long localSidTransactionId;
    @Column(name = "local_source_type", length = 24)
    private String localSourceType;
    @Column(name = "incoming_lis_file_id")
    private Long incomingLisFileId;
    @Column(name = "incoming_record_sequence")
    private Integer incomingRecordSequence;
    @Column(name = "functional_key", nullable = false, length = 64)
    private String functionalKey;
    @Column(name = "transaction_type", nullable = false, length = 16)
    private String transactionType;
    @Column(name = "clearing_cycle", nullable = false)
    private int clearingCycle = 1;
    @Column(name = "pan_fingerprint", nullable = false, length = 64)
    private String panFingerprint;
    @Column(name = "masked_pan", nullable = false, length = 24)
    private String maskedPan;
    @Column(length = 12)
    private String rrn;
    @Column(length = 6)
    private String stan;
    @Column(name = "authorization_code", length = 6)
    private String authorizationCode;
    @Column(name = "transaction_at")
    private LocalDateTime transactionAt;
    @Column(name = "processing_date")
    private LocalDate processingDate;
    @Column(name = "processing_code", length = 6)
    private String processingCode;
    @Column(length = 4)
    private String mcc;
    @Column(name = "pos_data_code", length = 12)
    private String posDataCode;
    @Column(name = "terminal_id", length = 8)
    private String terminalId;
    @Column(name = "merchant_id", length = 15)
    private String merchantId;
    @Column(name = "merchant_name", length = 25)
    private String merchantName;
    @Column(name = "merchant_city", length = 13)
    private String merchantCity;
    @Column(name = "transaction_amount", nullable = false)
    private long transactionAmount;
    @Column(name = "transaction_currency", nullable = false, length = 3)
    private String transactionCurrency;
    @Column(name = "billing_amount")
    private Long billingAmount;
    @Column(name = "billing_currency", length = 3)
    private String billingCurrency;
    @Column(name = "settlement_amount")
    private Long settlementAmount;
    @Column(name = "settlement_currency", length = 3)
    private String settlementCurrency;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_presence", nullable = false, length = 16)
    private SourcePresence sourcePresence;
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 24)
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;
    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_status", nullable = false, length = 16)
    private AccountingStatus accountingStatus = AccountingStatus.PENDING;
    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 16)
    private DisputeStatus disputeStatus = DisputeStatus.NONE;
    @Column(name = "manual_reason", length = 500)
    private String manualReason;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Version
    private long version;

    public Long getId() { return id; }
    public String getBankMemberId() { return bankMemberId; }
    public void setBankMemberId(String v) { bankMemberId = v; }
    public Long getBusinessDayId() { return businessDayId; }
    public void setBusinessDayId(Long v) { businessDayId = v; }
    public Long getLocalSidTransactionId() { return localSidTransactionId; }
    public void setLocalSidTransactionId(Long v) { localSidTransactionId = v; }
    public String getLocalSourceType() { return localSourceType; }
    public void setLocalSourceType(String v) { localSourceType = v; }
    public Long getIncomingLisFileId() { return incomingLisFileId; }
    public void setIncomingLisFileId(Long v) { incomingLisFileId = v; }
    public Integer getIncomingRecordSequence() { return incomingRecordSequence; }
    public void setIncomingRecordSequence(Integer v) { incomingRecordSequence = v; }
    public String getFunctionalKey() { return functionalKey; }
    public void setFunctionalKey(String v) { functionalKey = v; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { transactionType = v; }
    public int getClearingCycle() { return clearingCycle; }
    public void setClearingCycle(int v) { clearingCycle = v; }
    public String getPanFingerprint() { return panFingerprint; }
    public void setPanFingerprint(String v) { panFingerprint = v; }
    public String getMaskedPan() { return maskedPan; }
    public void setMaskedPan(String v) { maskedPan = v; }
    public String getRrn() { return rrn; }
    public void setRrn(String v) { rrn = v; }
    public String getStan() { return stan; }
    public void setStan(String v) { stan = v; }
    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String v) { authorizationCode = v; }
    public LocalDateTime getTransactionAt() { return transactionAt; }
    public void setTransactionAt(LocalDateTime v) { transactionAt = v; }
    public LocalDate getProcessingDate() { return processingDate; }
    public void setProcessingDate(LocalDate v) { processingDate = v; }
    public String getProcessingCode() { return processingCode; }
    public void setProcessingCode(String v) { processingCode = v; }
    public String getMcc() { return mcc; }
    public void setMcc(String v) { mcc = v; }
    public String getPosDataCode() { return posDataCode; }
    public void setPosDataCode(String v) { posDataCode = v; }
    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String v) { terminalId = v; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String v) { merchantId = v; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String v) { merchantName = v; }
    public String getMerchantCity() { return merchantCity; }
    public void setMerchantCity(String v) { merchantCity = v; }
    public long getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(long v) { transactionAmount = v; }
    public String getTransactionCurrency() { return transactionCurrency; }
    public void setTransactionCurrency(String v) { transactionCurrency = v; }
    public Long getBillingAmount() { return billingAmount; }
    public void setBillingAmount(Long v) { billingAmount = v; }
    public String getBillingCurrency() { return billingCurrency; }
    public void setBillingCurrency(String v) { billingCurrency = v; }
    public Long getSettlementAmount() { return settlementAmount; }
    public void setSettlementAmount(Long v) { settlementAmount = v; }
    public String getSettlementCurrency() { return settlementCurrency; }
    public void setSettlementCurrency(String v) { settlementCurrency = v; }
    public SourcePresence getSourcePresence() { return sourcePresence; }
    public void setSourcePresence(SourcePresence v) { sourcePresence = v; }
    public MatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(MatchStatus v) { matchStatus = v; }
    public AccountingStatus getAccountingStatus() { return accountingStatus; }
    public void setAccountingStatus(AccountingStatus v) { accountingStatus = v; }
    public DisputeStatus getDisputeStatus() { return disputeStatus; }
    public void setDisputeStatus(DisputeStatus v) { disputeStatus = v; }
    public String getManualReason() { return manualReason; }
    public void setManualReason(String v) { manualReason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public long getVersion() { return version; }
}
