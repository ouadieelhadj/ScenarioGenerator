package com.staging.sg.mc.sms.acquirer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_sms_acq_transactions")
public class McSmsAcqTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pan",             nullable = false) private String pan;
    @Column(name = "stan",            nullable = false) private String stan;
    @Column(name = "transmission_dt", nullable = false) private String transmissionDt;
    @Column(name = "mti",             nullable = false) private String mti;
    @Column(name = "processing_code")                  private String processingCode;
    @Column(name = "amount",          nullable = false) private Long amount;
    @Column(name = "currency")                         private String currency;
    @Column(name = "response_code")                    private String responseCode;   // an-2
    @Column(name = "auth_id_response")                 private String authIdResponse; // DE38
    @Column(name = "network_id")                       private String networkId;      // DE24
    @Column(name = "retrieval_ref")                    private String retrievalRef;   // DE37
    @Column(name = "status",          nullable = false) private String status = "SENT";
    @Column(name = "created_at")                       private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
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
    public String getAuthIdResponse() { return authIdResponse; }
    public void setAuthIdResponse(String v) { this.authIdResponse = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
