package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_executions")
public class CampaignExecution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20) private String status;
    @Column(name = "tps_target")           private Integer tpsTarget;
    @Column(name = "duration_seconds")     private Integer durationSeconds;
    @Column(name = "tx_total")    private Integer txTotal = 0;
    @Column(name = "tx_sent")     private Integer txSent = 0;
    @Column(name = "tx_approved") private Integer txApproved = 0;
    @Column(name = "tx_declined") private Integer txDeclined = 0;
    @Column(name = "tps_actual_avg",    precision = 10, scale = 2) private BigDecimal tpsActualAvg;
    @Column(name = "response_time_avg", precision = 10, scale = 2) private BigDecimal responseTimeAvg;
    @Column(name = "response_time_min", precision = 10, scale = 2) private BigDecimal responseTimeMin;
    @Column(name = "response_time_max", precision = 10, scale = 2) private BigDecimal responseTimeMax;
    @Column(name = "response_time_p95", precision = 10, scale = 2) private BigDecimal responseTimeP95;
    @Column(name = "response_time_p99", precision = 10, scale = 2) private BigDecimal responseTimeP99;
    @Column(length = 10)                   private String verdict;
    @Column(name = "verdict_detail", length = 255) private String verdictDetail;
    @Column(name = "started_at") private LocalDateTime startedAt = LocalDateTime.now();
    @Column(name = "ended_at")   private LocalDateTime endedAt;
    @Column(name = "report_dir",   length = 255) private String reportDir;
    @Column(name = "report_pdf",   length = 255) private String reportPdf;
    @Column(name = "report_excel", length = 255) private String reportExcel;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign v) { this.campaign = v; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
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
    public String getVerdict() { return verdict; }
    public void setVerdict(String v) { this.verdict = v; }
    public String getVerdictDetail() { return verdictDetail; }
    public void setVerdictDetail(String v) { this.verdictDetail = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime v) { this.endedAt = v; }
    public String getReportDir() { return reportDir; }
    public void setReportDir(String v) { this.reportDir = v; }
    public String getReportPdf() { return reportPdf; }
    public void setReportPdf(String v) { this.reportPdf = v; }
    public String getReportExcel() { return reportExcel; }
    public void setReportExcel(String v) { this.reportExcel = v; }
}
