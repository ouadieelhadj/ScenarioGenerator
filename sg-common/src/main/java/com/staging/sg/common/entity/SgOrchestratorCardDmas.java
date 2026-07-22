package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Carte de test appartenant a L'ORCHESTRATEUR, pour le reseau DMAS.
 *
 * L'orchestrateur joue le role du terminal : il possede ses propres
 * cartes et ne lit PAS mc_dmas_cards (table metier du membre). Cela
 * respecte la separation des responsabilites entre modules.
 *
 * Nom prefixe par reseau (sg_orchestrator_cards_dmas) pour distinguer
 * DMAS de SWAM et MC SMS a venir.
 */
@Entity
@Table(name = "sg_orchestrator_cards_dmas")
public class SgOrchestratorCardDmas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pan", length = 19, unique = true) private String pan;
    @Column(name = "pin", length = 12)                private String pin;
    @Column(name = "balance")                          private Long balance = 0L;
    @Column(name = "currency", length = 3)            private String currency = "840";
    @Column(name = "expiry",   length = 4)            private String expiry;
    @Column(name = "status",   length = 10)           private String status = "ACTIVE";
    @Column(name = "bank_code", length = 6)           private String bankCode;

    // Donnees EMV : l'orchestrateur est le terminal
    @Column(name = "emv_aid",         length = 32) private String emvAid;
    @Column(name = "emv_aip",         length = 4)  private String emvAip;
    @Column(name = "emv_psn",         length = 2)  private String emvPsn;
    @Column(name = "emv_atc")                       private Integer emvAtc = 0;
    @Column(name = "emv_app_version", length = 4)  private String emvAppVersion;
    @Column(name = "emv_iad",         length = 64) private String emvIad;
    @Column(name = "emv_cvm_results", length = 6)  private String emvCvmResults;

    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getPan() { return pan; }
    public void setPan(String v) { this.pan = v; }
    public String getPin() { return pin; }
    public void setPin(String v) { this.pin = v; }
    public Long getBalance() { return balance; }
    public void setBalance(Long v) { this.balance = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String v) { this.expiry = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
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
