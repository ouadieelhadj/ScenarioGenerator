package com.staging.sg.mc.sms.issuer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_sms_iss_transactions")
public class McSmsIssTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pan",             nullable = false) private String pan;
    @Column(name = "stan",            nullable = false) private String stan;
    @Column(name = "transmission_dt", nullable = false) private String transmissionDt;
    @Column(name = "mti",             nullable = false) private String mti;
    @Column(name = "processing_code")                  private String processingCode;
    @Column(name = "amount",          nullable = false) private Long amount;
    @Column(name = "currency")                         private String currency;
    @Column(name = "response_code")                    private String responseCode;
    @Column(name = "auth_id_response")                 private String authIdResponse;
    @Column(name = "retrieval_ref")                    private String retrievalRef;
    @Column(name = "status",          nullable = false) private String status = "APPROVED";
    @Column(name = "created_at")                       private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reversed_at")                      private LocalDateTime reversedAt;

    public Long getId() { return id; }
    public String getPan() { return pan; }
    public void setPan(String v) { this.pan = v; }
    public String getStan() { return stan; }
    public void setStan(String v) { this.stan = v; }
    public String getTransmissionDt() { return transmissionDt; }
    public void setTransmissionDt(String v) { this.transmissionDt = v; }
    public String getMti() { return mti; }
    public void setMti(String v) { this.mti = v; }
    public Long getAmount() { return amount; }
    public void setAmount(Long v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String v) { this.responseCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAuthIdResponse() { return authIdResponse; }
    public void setAuthIdResponse(String v) { this.authIdResponse = v; }
}
