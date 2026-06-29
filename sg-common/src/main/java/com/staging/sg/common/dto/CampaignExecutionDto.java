package com.staging.sg.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Representation de sortie d'une execution de campagne (suivi / resultat). */
public class CampaignExecutionDto {

    private Long id;
    private Long campaignId;
    private String campaignName;
    private String status;          // RUNNING | COMPLETED | STOPPED_ERROR_RATE | ERROR
    private String verdict;         // PASSED | FAILED | null
    private String verdictDetail;

    private Integer tpsTarget;
    private Integer durationSeconds;
    private Integer txTotal;
    private Integer txSent;
    private Integer txApproved;
    private Integer txDeclined;

    private BigDecimal tpsActualAvg;
    private BigDecimal responseTimeAvg;
    private BigDecimal responseTimeMin;
    private BigDecimal responseTimeMax;
    private BigDecimal responseTimeP95;
    private BigDecimal responseTimeP99;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long v) { this.campaignId = v; }
    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String v) { this.campaignName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String v) { this.verdict = v; }
    public String getVerdictDetail() { return verdictDetail; }
    public void setVerdictDetail(String v) { this.verdictDetail = v; }
    public Integer getTpsTarget() { return tpsTarget; }
    public void setTpsTarget(Integer v) { this.tpsTarget = v; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer v) { this.durationSeconds = v; }
    public Integer getTxTotal() { return txTotal; }
    public void setTxTotal(Integer v) { this.txTotal = v; }
    public Integer getTxSent() { return txSent; }
    public void setTxSent(Integer v) { this.txSent = v; }
    public Integer getTxApproved() { return txApproved; }
    public void setTxApproved(Integer v) { this.txApproved = v; }
    public Integer getTxDeclined() { return txDeclined; }
    public void setTxDeclined(Integer v) { this.txDeclined = v; }
    public BigDecimal getTpsActualAvg() { return tpsActualAvg; }
    public void setTpsActualAvg(BigDecimal v) { this.tpsActualAvg = v; }
    public BigDecimal getResponseTimeAvg() { return responseTimeAvg; }
    public void setResponseTimeAvg(BigDecimal v) { this.responseTimeAvg = v; }
    public BigDecimal getResponseTimeMin() { return responseTimeMin; }
    public void setResponseTimeMin(BigDecimal v) { this.responseTimeMin = v; }
    public BigDecimal getResponseTimeMax() { return responseTimeMax; }
    public void setResponseTimeMax(BigDecimal v) { this.responseTimeMax = v; }
    public BigDecimal getResponseTimeP95() { return responseTimeP95; }
    public void setResponseTimeP95(BigDecimal v) { this.responseTimeP95 = v; }
    public BigDecimal getResponseTimeP99() { return responseTimeP99; }
    public void setResponseTimeP99(BigDecimal v) { this.responseTimeP99 = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime v) { this.endedAt = v; }
}
