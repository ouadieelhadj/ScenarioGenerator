package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_dmas_cards")
public class McDmasCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pan",      length = 19, unique = true) private String pan;
    @Column(name = "pin",      length = 12) private String pin;       // PIN attendu (clair, simulateur)
    @Column(name = "balance")                private Long balance = 0L; // centimes
    @Column(name = "currency", length = 3)  private String currency = "840";
    @Column(name = "expiry",   length = 4)  private String expiry;     // YYMM
    @Column(name = "status",   length = 10) private String status = "ACTIVE";
    @Column(name = "created_at")             private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")             private LocalDateTime updatedAt = LocalDateTime.now();

    // --- multi-banque ---
    @Column(name = "bank_code", length = 6)  private String bankCode;

    // --- donnees EMV, pour construire le DE55 ---
    @Column(name = "emv_aid",         length = 32) private String emvAid;          // tag 84
    @Column(name = "emv_aip",         length = 4)  private String emvAip;          // tag 82
    @Column(name = "emv_psn",         length = 2)  private String emvPsn;          // PAN Sequence Number
    @Column(name = "emv_atc")                       private Integer emvAtc = 0;     // tag 9F36
    @Column(name = "emv_app_version", length = 4)  private String emvAppVersion;   // tag 9F09
    @Column(name = "emv_iad",         length = 64) private String emvIad;          // tag 9F10
    @Column(name = "emv_cvm_results", length = 6)  private String emvCvmResults;   // tag 9F34

    public McDmasCard() {}

    public Long          getId()        { return id; }
    public String        getPan()       { return pan; }
    public String        getPin()       { return pin; }
    public Long          getBalance()   { return balance; }
    public String        getCurrency()  { return currency; }
    public String        getExpiry()    { return expiry; }
    public String        getStatus()    { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long v)                 { this.id = v; }
    public void setPan(String v)              { this.pan = v; }
    public void setPin(String v)              { this.pin = v; }
    public void setBalance(Long v)            { this.balance = v; }
    public void setCurrency(String v)         { this.currency = v; }
    public void setExpiry(String v)           { this.expiry = v; }
    public void setStatus(String v)           { this.status = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String v) { this.bankCode = v; }
    public String getEmvAid() { return emvAid; }
    public void setEmvAid(String v) { this.emvAid = v; }
    public String getEmvAip() { return emvAip; }
    public void setEmvAip(String v) { this.emvAip = v; }
    public String getEmvPsn() { return emvPsn; }
    public void setEmvPsn(String v) { this.emvPsn = v; }
    public Integer getEmvAtc() { return emvAtc; }
    public void setEmvAtc(Integer v) { this.emvAtc = v; }
    public String getEmvAppVersion() { return emvAppVersion; }
    public void setEmvAppVersion(String v) { this.emvAppVersion = v; }
    public String getEmvIad() { return emvIad; }
    public void setEmvIad(String v) { this.emvIad = v; }
    public String getEmvCvmResults() { return emvCvmResults; }
    public void setEmvCvmResults(String v) { this.emvCvmResults = v; }
}
