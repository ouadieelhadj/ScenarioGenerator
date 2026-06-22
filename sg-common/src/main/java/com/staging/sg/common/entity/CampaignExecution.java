package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_executions")
public class CampaignExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false) private Long campaignId;
    @Column(length = 20, nullable = false)          private String status = "RUNNING";
    @Column(name = "tx_total")     private Integer txTotal = 0;
    @Column(name = "tx_approved")  private Integer txApproved = 0;
    @Column(name = "tx_declined")  private Integer txDeclined = 0;
    @Column(name = "tx_errors")    private Integer txErrors = 0;
    @Column(name = "response_time_avg", precision = 10, scale = 2) private BigDecimal responseTimeAvg;
    @Column(name = "response_time_min", precision = 10, scale = 2) private BigDecimal responseTimeMin;
    @Column(name = "response_time_max", precision = 10, scale = 2) private BigDecimal responseTimeMax;
    @Column(name = "response_time_p95", precision = 10, scale = 2) private BigDecimal responseTimeP95;
    @Column(name = "response_time_p99", precision = 10, scale = 2) private BigDecimal responseTimeP99;
    @Column(name = "started_at")   private LocalDateTime startedAt = LocalDateTime.now();
    @Column(name = "ended_at")     private LocalDateTime endedAt;

    public CampaignExecution() {}

    public Long getId()                 { return id; }
    public Long getCampaignId()         { return campaignId; }
    public String getStatus()           { return status; }
    public Integer getTxTotal()         { return txTotal; }
    public Integer getTxApproved()      { return txApproved; }
    public Integer getTxDeclined()      { return txDeclined; }
    public Integer getTxErrors()        { return txErrors; }
    public BigDecimal getResponseTimeAvg() { return responseTimeAvg; }
    public BigDecimal getResponseTimeMin() { return responseTimeMin; }
    public BigDecimal getResponseTimeMax() { return responseTimeMax; }
    public BigDecimal getResponseTimeP95() { return responseTimeP95; }
    public BigDecimal getResponseTimeP99() { return responseTimeP99; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt()   { return endedAt; }

    public void setId(Long v)                 { this.id = v; }
    public void setCampaignId(Long v)         { this.campaignId = v; }
    public void setStatus(String v)           { this.status = v; }
    public void setTxTotal(Integer v)         { this.txTotal = v; }
    public void setTxApproved(Integer v)      { this.txApproved = v; }
    public void setTxDeclined(Integer v)      { this.txDeclined = v; }
    public void setTxErrors(Integer v)        { this.txErrors = v; }
    public void setResponseTimeAvg(BigDecimal v) { this.responseTimeAvg = v; }
    public void setResponseTimeMin(BigDecimal v) { this.responseTimeMin = v; }
    public void setResponseTimeMax(BigDecimal v) { this.responseTimeMax = v; }
    public void setResponseTimeP95(BigDecimal v) { this.responseTimeP95 = v; }
    public void setResponseTimeP99(BigDecimal v) { this.responseTimeP99 = v; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public void setEndedAt(LocalDateTime v)   { this.endedAt = v; }
}
