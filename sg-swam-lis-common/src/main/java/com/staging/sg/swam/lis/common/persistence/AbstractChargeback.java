package com.staging.sg.swam.lis.common.persistence;

import com.staging.sg.swam.lis.common.model.ChargebackDirection;
import com.staging.sg.swam.lis.common.model.ChargebackStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AbstractChargeback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "bank_member_id", nullable = false, length = 20)
    private String bankMemberId;
    @Column(name = "clearing_transaction_id", nullable = false)
    private Long clearingTransactionId;
    @Column(name = "parent_chargeback_id")
    private Long parentChargebackId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChargebackDirection direction;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChargebackStatus status = ChargebackStatus.DRAFT;
    @Column(name = "transaction_code", nullable = false, length = 2)
    private String transactionCode;
    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber = 1;
    @Column(name = "reason_code", nullable = false, length = 4)
    private String reasonCode;
    @Column(name = "chargeback_reference", nullable = false, length = 6)
    private String chargebackReference;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "source_lis_file_id")
    private Long sourceLisFileId;
    @Column(name = "source_record_sequence")
    private Integer sourceRecordSequence;
    @Column(name = "outgoing_lis_file_id")
    private Long outgoingLisFileId;
    @Column(name = "counterparty_member", nullable = false, length = 20)
    private String counterpartyMember;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "emitted_at")
    private LocalDateTime emittedAt;
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;
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
    public Long getClearingTransactionId() { return clearingTransactionId; }
    public void setClearingTransactionId(Long v) { clearingTransactionId = v; }
    public Long getParentChargebackId() { return parentChargebackId; }
    public void setParentChargebackId(Long v) { parentChargebackId = v; }
    public ChargebackDirection getDirection() { return direction; }
    public void setDirection(ChargebackDirection v) { direction = v; }
    public ChargebackStatus getStatus() { return status; }
    public void setStatus(ChargebackStatus v) { status = v; }
    public String getTransactionCode() { return transactionCode; }
    public void setTransactionCode(String v) { transactionCode = v; }
    public int getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(int v) { cycleNumber = v; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String v) { reasonCode = v; }
    public String getChargebackReference() { return chargebackReference; }
    public void setChargebackReference(String v) { chargebackReference = v; }
    public long getAmount() { return amount; }
    public void setAmount(long v) { amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v; }
    public Long getSourceLisFileId() { return sourceLisFileId; }
    public void setSourceLisFileId(Long v) { sourceLisFileId = v; }
    public Integer getSourceRecordSequence() { return sourceRecordSequence; }
    public void setSourceRecordSequence(Integer v) { sourceRecordSequence = v; }
    public Long getOutgoingLisFileId() { return outgoingLisFileId; }
    public void setOutgoingLisFileId(Long v) { outgoingLisFileId = v; }
    public String getCounterpartyMember() { return counterpartyMember; }
    public void setCounterpartyMember(String v) { counterpartyMember = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { dueDate = v; }
    public LocalDateTime getEmittedAt() { return emittedAt; }
    public void setEmittedAt(LocalDateTime v) { emittedAt = v; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime v) { receivedAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }
    public String getManualReason() { return manualReason; }
    public void setManualReason(String v) { manualReason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public long getVersion() { return version; }
}
