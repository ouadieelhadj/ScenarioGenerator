package com.staging.sg.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vue consolidee d'une operation de clearing DMC.
 *
 * <p>Une ligne peut provenir du journal d'autorisation local ou d'un fichier
 * IPM entrant. Les classes concretes isolent physiquement les donnees du
 * membre et celles du simulateur Mastercard.</p>
 */
@MappedSuperclass
public abstract class AbstractDmcClearingTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;
    @Column(name = "source_type", length = 20, nullable = false)
    private String sourceType;
    @Column(name = "direction", length = 3, nullable = false)
    private String direction;
    @Column(name = "local_authorization_id")
    private Long localAuthorizationId;
    @Column(name = "source_file_id")
    private Long sourceFileId;
    @Column(name = "source_message_number")
    private Integer sourceMessageNumber;
    @Column(name = "parent_transaction_id")
    private Long parentTransactionId;
    @Column(name = "correlation_key", length = 80, nullable = false)
    private String correlationKey;
    @Column(name = "lifecycle_stage", length = 24, nullable = false)
    private String lifecycleStage;
    @Column(name = "status", length = 24, nullable = false)
    private String status;
    @Column(name = "match_status", length = 24, nullable = false)
    private String matchStatus;

    @Column(name = "mti", length = 4, nullable = false)
    private String mti;
    @Column(name = "function_code", length = 3, nullable = false)
    private String functionCode;
    @Column(name = "de002_pan", length = 19, nullable = false)
    private String pan;
    @Column(name = "masked_pan", length = 19, nullable = false)
    private String maskedPan;
    @Column(name = "de003_processing_code", length = 6)
    private String processingCode;
    @Column(name = "de004_amount")
    private Long amount;
    @Column(name = "de005_reconciliation_amount")
    private Long reconciliationAmount;
    @Column(name = "de009_reconciliation_rate", length = 8)
    private String reconciliationRate;
    @Column(name = "de012_transaction_datetime", length = 12)
    private String transactionDatetime;
    @Column(name = "de014_expiry", length = 4)
    private String expiry;
    @Column(name = "de022_pos_data_code", length = 12)
    private String posDataCode;
    @Column(name = "de025_message_reason_code", length = 4)
    private String messageReasonCode;
    @Column(name = "de026_mcc", length = 4)
    private String mcc;
    @Column(name = "de030_original_amounts", length = 24)
    private String originalAmounts;
    @Column(name = "de031_acquirer_reference", length = 23)
    private String acquirerReference;
    @Column(name = "de032_acquiring_id", length = 11)
    private String acquiringInstitutionId;
    @Column(name = "de033_forwarding_id", length = 11)
    private String forwardingInstitutionId;
    @Column(name = "de037_rrn", length = 12)
    private String rrn;
    @Column(name = "de038_authorization_code", length = 6)
    private String authorizationCode;
    @Column(name = "de041_terminal_id", length = 8)
    private String terminalId;
    @Column(name = "de042_acceptor_id", length = 15)
    private String acceptorId;
    @Column(name = "de043_acceptor_name_location", length = 99)
    private String acceptorNameLocation;
    @Column(name = "de049_currency", length = 3)
    private String currency;
    @Column(name = "de050_reconciliation_currency", length = 3)
    private String reconciliationCurrency;
    @Column(name = "de071_message_number", length = 8)
    private String messageNumber;
    @Column(name = "de093_destination_id", length = 11)
    private String destinationId;
    @Column(name = "de094_origin_id", length = 11)
    private String originId;
    @Column(name = "de095_issuer_reference", length = 42)
    private String issuerReference;
    @Column(name = "pds_data", columnDefinition = "TEXT")
    private String pdsData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getSourceType() { return sourceType; }
    public String getDirection() { return direction; }
    public Long getLocalAuthorizationId() { return localAuthorizationId; }
    public Long getSourceFileId() { return sourceFileId; }
    public Integer getSourceMessageNumber() { return sourceMessageNumber; }
    public Long getParentTransactionId() { return parentTransactionId; }
    public String getCorrelationKey() { return correlationKey; }
    public String getLifecycleStage() { return lifecycleStage; }
    public String getStatus() { return status; }
    public String getMatchStatus() { return matchStatus; }
    public String getMti() { return mti; }
    public String getFunctionCode() { return functionCode; }
    public String getPan() { return pan; }
    public String getMaskedPan() { return maskedPan; }
    public String getProcessingCode() { return processingCode; }
    public Long getAmount() { return amount; }
    public Long getReconciliationAmount() { return reconciliationAmount; }
    public String getReconciliationRate() { return reconciliationRate; }
    public String getTransactionDatetime() { return transactionDatetime; }
    public String getExpiry() { return expiry; }
    public String getPosDataCode() { return posDataCode; }
    public String getMessageReasonCode() { return messageReasonCode; }
    public String getMcc() { return mcc; }
    public String getOriginalAmounts() { return originalAmounts; }
    public String getAcquirerReference() { return acquirerReference; }
    public String getAcquiringInstitutionId() { return acquiringInstitutionId; }
    public String getForwardingInstitutionId() { return forwardingInstitutionId; }
    public String getRrn() { return rrn; }
    public String getAuthorizationCode() { return authorizationCode; }
    public String getTerminalId() { return terminalId; }
    public String getAcceptorId() { return acceptorId; }
    public String getAcceptorNameLocation() { return acceptorNameLocation; }
    public String getCurrency() { return currency; }
    public String getReconciliationCurrency() { return reconciliationCurrency; }
    public String getMessageNumber() { return messageNumber; }
    public String getDestinationId() { return destinationId; }
    public String getOriginId() { return originId; }
    public String getIssuerReference() { return issuerReference; }
    public String getPdsData() { return pdsData; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setId(Long id) { this.id = id; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setLocalAuthorizationId(Long localAuthorizationId) { this.localAuthorizationId = localAuthorizationId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }
    public void setSourceMessageNumber(Integer sourceMessageNumber) { this.sourceMessageNumber = sourceMessageNumber; }
    public void setParentTransactionId(Long parentTransactionId) { this.parentTransactionId = parentTransactionId; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }
    public void setLifecycleStage(String lifecycleStage) { this.lifecycleStage = lifecycleStage; }
    public void setStatus(String status) { this.status = status; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public void setMti(String mti) { this.mti = mti; }
    public void setFunctionCode(String functionCode) { this.functionCode = functionCode; }
    public void setPan(String pan) { this.pan = pan; }
    public void setMaskedPan(String maskedPan) { this.maskedPan = maskedPan; }
    public void setProcessingCode(String processingCode) { this.processingCode = processingCode; }
    public void setAmount(Long amount) { this.amount = amount; }
    public void setReconciliationAmount(Long reconciliationAmount) { this.reconciliationAmount = reconciliationAmount; }
    public void setReconciliationRate(String reconciliationRate) { this.reconciliationRate = reconciliationRate; }
    public void setTransactionDatetime(String transactionDatetime) { this.transactionDatetime = transactionDatetime; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public void setPosDataCode(String posDataCode) { this.posDataCode = posDataCode; }
    public void setMessageReasonCode(String messageReasonCode) { this.messageReasonCode = messageReasonCode; }
    public void setMcc(String mcc) { this.mcc = mcc; }
    public void setOriginalAmounts(String originalAmounts) { this.originalAmounts = originalAmounts; }
    public void setAcquirerReference(String acquirerReference) { this.acquirerReference = acquirerReference; }
    public void setAcquiringInstitutionId(String acquiringInstitutionId) { this.acquiringInstitutionId = acquiringInstitutionId; }
    public void setForwardingInstitutionId(String forwardingInstitutionId) { this.forwardingInstitutionId = forwardingInstitutionId; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
    public void setAcceptorId(String acceptorId) { this.acceptorId = acceptorId; }
    public void setAcceptorNameLocation(String acceptorNameLocation) { this.acceptorNameLocation = acceptorNameLocation; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setReconciliationCurrency(String reconciliationCurrency) { this.reconciliationCurrency = reconciliationCurrency; }
    public void setMessageNumber(String messageNumber) { this.messageNumber = messageNumber; }
    public void setDestinationId(String destinationId) { this.destinationId = destinationId; }
    public void setOriginId(String originId) { this.originId = originId; }
    public void setIssuerReference(String issuerReference) { this.issuerReference = issuerReference; }
    public void setPdsData(String pdsData) { this.pdsData = pdsData; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setVersion(Long version) { this.version = version; }
}
