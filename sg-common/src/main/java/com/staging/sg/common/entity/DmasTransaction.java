package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dmas_transactions")
public class DmasTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pan",             length = 19) private String pan;
    @Column(name = "stan",            length = 6)  private String stan;
    @Column(name = "transmission_dt", length = 10) private String transmissionDt;
    @Column(name = "mti",             length = 4)  private String mti;
    @Column(name = "processing_code", length = 6)  private String processingCode;
    @Column(name = "amount")                        private Long amount;
    @Column(name = "currency",        length = 3)  private String currency;
    @Column(name = "response_code",   length = 2)  private String responseCode;
    @Column(name = "status",          length = 10) private String status = "APPROVED";
    @Column(name = "created_at")                    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reversed_at")                   private LocalDateTime reversedAt;

    public DmasTransaction() {}

    public Long getId()                   { return id; }
    public String getPan()                { return pan; }
    public String getStan()               { return stan; }
    public String getTransmissionDt()     { return transmissionDt; }
    public String getMti()                { return mti; }
    public String getProcessingCode()     { return processingCode; }
    public Long getAmount()               { return amount; }
    public String getCurrency()           { return currency; }
    public String getResponseCode()       { return responseCode; }
    public String getStatus()             { return status; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public LocalDateTime getReversedAt()  { return reversedAt; }

    public void setId(Long v)                { this.id = v; }
    public void setPan(String v)             { this.pan = v; }
    public void setStan(String v)            { this.stan = v; }
    public void setTransmissionDt(String v)  { this.transmissionDt = v; }
    public void setMti(String v)             { this.mti = v; }
    public void setProcessingCode(String v)  { this.processingCode = v; }
    public void setAmount(Long v)            { this.amount = v; }
    public void setCurrency(String v)        { this.currency = v; }
    public void setResponseCode(String v)    { this.responseCode = v; }
    public void setStatus(String v)          { this.status = v; }
    public void setCreatedAt(LocalDateTime v){ this.createdAt = v; }
    public void setReversedAt(LocalDateTime v){ this.reversedAt = v; }
}
