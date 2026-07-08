package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Transaction emise par l'acquereur (table swam_acq_transactions). */
@Entity
@Table(name = "swam_acq_transactions")
public class SwamAcqTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 19)               private String pan;
    @Column(nullable = false, length = 6)                private String stan;
    @Column(name = "transmission_dt", nullable = false, length = 10) private String transmissionDt;
    @Column(nullable = false, length = 4)                private String mti;
    @Column(name = "processing_code", length = 6)        private String processingCode;
    @Column(nullable = false)                            private Long amount;
    @Column(length = 3)                                  private String currency;
    @Column(name = "response_code", length = 3)          private String responseCode;
    @Column(nullable = false, length = 10)               private String status = "SENT";
    @Column(name = "created_at")                         private LocalDateTime createdAt = LocalDateTime.now();

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
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String v) { this.responseCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
