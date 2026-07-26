package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Transaction autorisee par le switch (table swam_iss_transactions). */
@Entity
@Table(name = "swam_iss_transactions")
public class SwamIssTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 19)               private String pan;
    @Column(nullable = false, length = 6)                private String stan;
    @Column(name = "transmission_dt", nullable = false, length = 10) private String transmissionDt;
    @Column(nullable = false, length = 4)                private String mti;
    @Column(name = "processing_code", length = 6)        private String processingCode;
    @Column(nullable = false)                            private Long amount;
    @Column(length = 3)                                  private String currency;
    @Column(name = "local_transaction_dt", length = 12)  private String localTransactionDt;
    @Column(name = "settlement_date", length = 6)        private String settlementDate;
    @Column(name = "conversion_date", length = 4)        private String conversionDate;
    @Column(name = "expiry_date", length = 4)            private String expiryDate;
    @Column(name = "merchant_category_code", length = 4) private String merchantCategoryCode;
    @Column(name = "acquirer_country_code", length = 3)  private String acquirerCountryCode;
    @Column(name = "forwarding_country_code", length = 3) private String forwardingCountryCode;
    @Column(name = "pos_data_code", length = 12)         private String posDataCode;
    @Column(name = "function_code", length = 3)          private String functionCode;
    @Column(name = "message_reason_code", length = 4)    private String messageReasonCode;
    @Column(name = "card_sequence_number", length = 3)   private String cardSequenceNumber;
    @Column(name = "acquirer_institution_id", length = 11) private String acquirerInstitutionId;
    @Column(name = "forwarding_institution_id", length = 11) private String forwardingInstitutionId;
    @Column(length = 12)                                 private String rrn;
    @Column(name = "authorization_code", length = 6)     private String authorizationCode;
    @Column(name = "terminal_id", length = 8)            private String terminalId;
    @Column(name = "merchant_id", length = 15)           private String merchantId;
    @Column(name = "merchant_name_location", length = 40) private String merchantNameLocation;
    @Column(name = "settlement_amount")                  private Long settlementAmount;
    @Column(name = "billing_amount")                     private Long billingAmount;
    @Column(name = "settlement_currency", length = 3)    private String settlementCurrency;
    @Column(name = "billing_currency", length = 3)       private String billingCurrency;
    @Column(name = "security_control_info", length = 99) private String securityControlInfo;
    @Column(name = "original_data_elements", length = 35) private String originalDataElements;
    @Column(name = "sender_identification", length = 999) private String senderIdentification;
    @Column(name = "clearing_eligible", nullable = false) private boolean clearingEligible;
    @Column(name = "clearing_amount")                    private Long clearingAmount;
    @Column(name = "lifecycle_status", length = 24)      private String lifecycleStatus;
    @Column(name = "response_code", length = 3)          private String responseCode;
    @Column(nullable = false, length = 10)               private String status = "APPROVED";
    @Column(name = "created_at")                         private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reversed_at")                        private LocalDateTime reversedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getPan() { return pan; }
    public void setPan(String v) { this.pan = v; }
    public String getStan() { return stan; }
    public void setStan(String v) { this.stan = v; }
    public String getTransmissionDt() { return transmissionDt; }
    public void setTransmissionDt(String v) { this.transmissionDt = v; }
    public String getMti() { return mti; }
    public void setMti(String v) { this.mti = v; }
    public String getProcessingCode() { return processingCode; }
    public void setProcessingCode(String v) { this.processingCode = v; }
    public Long getAmount() { return amount; }
    public void setAmount(Long v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getLocalTransactionDt() { return localTransactionDt; }
    public void setLocalTransactionDt(String v) { this.localTransactionDt = v; }
    public String getSettlementDate() { return settlementDate; }
    public void setSettlementDate(String v) { this.settlementDate = v; }
    public String getConversionDate() { return conversionDate; }
    public void setConversionDate(String v) { this.conversionDate = v; }
    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String v) { this.expiryDate = v; }
    public String getMerchantCategoryCode() { return merchantCategoryCode; }
    public void setMerchantCategoryCode(String v) { this.merchantCategoryCode = v; }
    public String getAcquirerCountryCode() { return acquirerCountryCode; }
    public void setAcquirerCountryCode(String v) { this.acquirerCountryCode = v; }
    public String getForwardingCountryCode() { return forwardingCountryCode; }
    public void setForwardingCountryCode(String v) { this.forwardingCountryCode = v; }
    public String getPosDataCode() { return posDataCode; }
    public void setPosDataCode(String v) { this.posDataCode = v; }
    public String getFunctionCode() { return functionCode; }
    public void setFunctionCode(String v) { this.functionCode = v; }
    public String getMessageReasonCode() { return messageReasonCode; }
    public void setMessageReasonCode(String v) { this.messageReasonCode = v; }
    public String getCardSequenceNumber() { return cardSequenceNumber; }
    public void setCardSequenceNumber(String v) { this.cardSequenceNumber = v; }
    public String getAcquirerInstitutionId() { return acquirerInstitutionId; }
    public void setAcquirerInstitutionId(String v) { this.acquirerInstitutionId = v; }
    public String getForwardingInstitutionId() { return forwardingInstitutionId; }
    public void setForwardingInstitutionId(String v) { this.forwardingInstitutionId = v; }
    public String getRrn() { return rrn; }
    public void setRrn(String v) { this.rrn = v; }
    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String v) { this.authorizationCode = v; }
    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String v) { this.terminalId = v; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String v) { this.merchantId = v; }
    public String getMerchantNameLocation() { return merchantNameLocation; }
    public void setMerchantNameLocation(String v) { this.merchantNameLocation = v; }
    public Long getSettlementAmount() { return settlementAmount; }
    public void setSettlementAmount(Long v) { this.settlementAmount = v; }
    public Long getBillingAmount() { return billingAmount; }
    public void setBillingAmount(Long v) { this.billingAmount = v; }
    public String getSettlementCurrency() { return settlementCurrency; }
    public void setSettlementCurrency(String v) { this.settlementCurrency = v; }
    public String getBillingCurrency() { return billingCurrency; }
    public void setBillingCurrency(String v) { this.billingCurrency = v; }
    public String getSecurityControlInfo() { return securityControlInfo; }
    public void setSecurityControlInfo(String v) { this.securityControlInfo = v; }
    public String getOriginalDataElements() { return originalDataElements; }
    public void setOriginalDataElements(String v) { this.originalDataElements = v; }
    public String getSenderIdentification() { return senderIdentification; }
    public void setSenderIdentification(String v) { this.senderIdentification = v; }
    public boolean isClearingEligible() { return clearingEligible; }
    public void setClearingEligible(boolean v) { this.clearingEligible = v; }
    public Long getClearingAmount() { return clearingAmount; }
    public void setClearingAmount(Long v) { this.clearingAmount = v; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String v) { this.lifecycleStatus = v; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String v) { this.responseCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public void setReversedAt(LocalDateTime v) { this.reversedAt = v; }
}
