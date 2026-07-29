package com.staging.sg.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Journal d'autorisation DMAS nécessaire au clearing DMC.
 *
 * <p>Le membre et l'issuer utilisent deux tables et deux entités concrètes
 * différentes. Cette superclasse ne crée aucune table et ne rend pas les
 * données partageables entre applications.</p>
 */
@MappedSuperclass
public abstract class AbstractMcDmasAuthorizationTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interface_id", length = 32)
    private String interfaceId;

    @Column(name = "bank_code", length = 6, nullable = false)
    private String bankCode;

    @Column(name = "mti_request", length = 4, nullable = false)
    private String mtiRequest;

    @Column(name = "mti_response", length = 4)
    private String mtiResponse;

    @Column(name = "pan", length = 19, nullable = false)
    private String pan;

    @Column(name = "masked_pan", length = 19, nullable = false)
    private String maskedPan;

    @Column(name = "de003_processing_code", length = 6)
    private String processingCode;

    @Column(name = "de004_amount")
    private Long amount;

    @Column(name = "de007_transmission_datetime", length = 10, nullable = false)
    private String transmissionDatetime;

    @Column(name = "de011_stan", length = 6, nullable = false)
    private String stan;

    @Column(name = "de012_local_time", length = 6)
    private String localTime;

    @Column(name = "de013_local_date", length = 4)
    private String localDate;

    @Column(name = "de014_expiry", length = 4)
    private String expiry;

    @Column(name = "de018_mcc", length = 4)
    private String mcc;

    @Column(name = "de022_pos_entry_mode", length = 3)
    private String posEntryMode;

    @Column(name = "de023_card_sequence", length = 3)
    private String cardSequence;

    @Column(name = "de032_acquiring_id", length = 11)
    private String acquiringInstitutionId;

    @Column(name = "de033_forwarding_id", length = 11)
    private String forwardingInstitutionId;

    @Column(name = "de037_rrn", length = 12)
    private String rrn;

    @Column(name = "de038_authorization_code", length = 6)
    private String authorizationCode;

    @Column(name = "de039_response_code", length = 2)
    private String responseCode;

    @Column(name = "de041_terminal_id", length = 8)
    private String terminalId;

    @Column(name = "de042_acceptor_id", length = 15)
    private String acceptorId;

    @Column(name = "de043_acceptor_name_location", length = 99)
    private String acceptorNameLocation;

    @Column(name = "de048_additional_data", length = 999)
    private String additionalData;

    @Column(name = "de049_currency", length = 3)
    private String currency;

    @Column(name = "de055_icc_data", columnDefinition = "TEXT")
    private String iccDataHex;

    @Column(name = "de061_pos_data", length = 128)
    private String posData;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Column(name = "clearing_eligible", nullable = false)
    private boolean clearingEligible;

    @Column(name = "clearing_extracted_at")
    private LocalDateTime clearingExtractedAt;

    @Column(name = "reversed", nullable = false)
    private boolean reversed;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Column(name = "request_at", nullable = false)
    private LocalDateTime requestAt;

    @Column(name = "response_at")
    private LocalDateTime responseAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Long getId() { return id; }
    public String getInterfaceId() { return interfaceId; }
    public String getBankCode() { return bankCode; }
    public String getMtiRequest() { return mtiRequest; }
    public String getMtiResponse() { return mtiResponse; }
    public String getPan() { return pan; }
    public String getMaskedPan() { return maskedPan; }
    public String getProcessingCode() { return processingCode; }
    public Long getAmount() { return amount; }
    public String getTransmissionDatetime() { return transmissionDatetime; }
    public String getStan() { return stan; }
    public String getLocalTime() { return localTime; }
    public String getLocalDate() { return localDate; }
    public String getExpiry() { return expiry; }
    public String getMcc() { return mcc; }
    public String getPosEntryMode() { return posEntryMode; }
    public String getCardSequence() { return cardSequence; }
    public String getAcquiringInstitutionId() { return acquiringInstitutionId; }
    public String getForwardingInstitutionId() { return forwardingInstitutionId; }
    public String getRrn() { return rrn; }
    public String getAuthorizationCode() { return authorizationCode; }
    public String getResponseCode() { return responseCode; }
    public String getTerminalId() { return terminalId; }
    public String getAcceptorId() { return acceptorId; }
    public String getAcceptorNameLocation() { return acceptorNameLocation; }
    public String getAdditionalData() { return additionalData; }
    public String getCurrency() { return currency; }
    public String getIccDataHex() { return iccDataHex; }
    public String getPosData() { return posData; }
    public boolean isApproved() { return approved; }
    public boolean isClearingEligible() { return clearingEligible; }
    public LocalDateTime getClearingExtractedAt() { return clearingExtractedAt; }
    public boolean isReversed() { return reversed; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public LocalDateTime getRequestAt() { return requestAt; }
    public LocalDateTime getResponseAt() { return responseAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setId(Long id) { this.id = id; }
    public void setInterfaceId(String interfaceId) { this.interfaceId = interfaceId; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public void setMtiRequest(String mtiRequest) { this.mtiRequest = mtiRequest; }
    public void setMtiResponse(String mtiResponse) { this.mtiResponse = mtiResponse; }
    public void setPan(String pan) { this.pan = pan; }
    public void setMaskedPan(String maskedPan) { this.maskedPan = maskedPan; }
    public void setProcessingCode(String processingCode) { this.processingCode = processingCode; }
    public void setAmount(Long amount) { this.amount = amount; }
    public void setTransmissionDatetime(String transmissionDatetime) { this.transmissionDatetime = transmissionDatetime; }
    public void setStan(String stan) { this.stan = stan; }
    public void setLocalTime(String localTime) { this.localTime = localTime; }
    public void setLocalDate(String localDate) { this.localDate = localDate; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public void setMcc(String mcc) { this.mcc = mcc; }
    public void setPosEntryMode(String posEntryMode) { this.posEntryMode = posEntryMode; }
    public void setCardSequence(String cardSequence) { this.cardSequence = cardSequence; }
    public void setAcquiringInstitutionId(String acquiringInstitutionId) { this.acquiringInstitutionId = acquiringInstitutionId; }
    public void setForwardingInstitutionId(String forwardingInstitutionId) { this.forwardingInstitutionId = forwardingInstitutionId; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
    public void setAcceptorId(String acceptorId) { this.acceptorId = acceptorId; }
    public void setAcceptorNameLocation(String acceptorNameLocation) { this.acceptorNameLocation = acceptorNameLocation; }
    public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setIccDataHex(String iccDataHex) { this.iccDataHex = iccDataHex; }
    public void setPosData(String posData) { this.posData = posData; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public void setClearingEligible(boolean clearingEligible) { this.clearingEligible = clearingEligible; }
    public void setClearingExtractedAt(LocalDateTime clearingExtractedAt) { this.clearingExtractedAt = clearingExtractedAt; }
    public void setReversed(boolean reversed) { this.reversed = reversed; }
    public void setReversedAt(LocalDateTime reversedAt) { this.reversedAt = reversedAt; }
    public void setRequestAt(LocalDateTime requestAt) { this.requestAt = requestAt; }
    public void setResponseAt(LocalDateTime responseAt) { this.responseAt = responseAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setVersion(Long version) { this.version = version; }
}
