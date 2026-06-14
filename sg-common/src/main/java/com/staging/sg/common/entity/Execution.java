package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "tps_target")
    private Integer tpsTarget;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "tx_total")
    private Integer txTotal = 0;

    @Column(name = "tx_sent")
    private Integer txSent = 0;

    @Column(name = "tx_approved")
    private Integer txApproved = 0;

    @Column(name = "tx_declined")
    private Integer txDeclined = 0;

    @Column(name = "tps_actual_avg", precision = 10, scale = 2)
    private BigDecimal tpsActualAvg;

    @Column(name = "response_time_avg", precision = 10, scale = 2)
    private BigDecimal responseTimeAvg;

    @Column(name = "response_time_min", precision = 10, scale = 2)
    private BigDecimal responseTimeMin;

    @Column(name = "response_time_max", precision = 10, scale = 2)
    private BigDecimal responseTimeMax;

    @Column(name = "response_time_p95", precision = 10, scale = 2)
    private BigDecimal responseTimeP95;

    @Column(name = "response_time_p99", precision = 10, scale = 2)
    private BigDecimal responseTimeP99;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "report_dir", length = 255)
    private String reportDir;

    @Column(name = "report_pdf", length = 255)
    private String reportPdf;

    @Column(name = "report_excel", length = 255)
    private String reportExcel;

    public Execution() {}

    // Getters
    public Long            getId()              { return id; }
    public User            getUser()            { return user; }
    public Test            getTest()            { return test; }
    public ExecutionMode   getMode()            { return mode; }
    public ExecutionStatus getStatus()          { return status; }
    public Integer         getTpsTarget()       { return tpsTarget; }
    public Integer         getDurationSeconds() { return durationSeconds; }
    public Integer         getTxTotal()         { return txTotal; }
    public Integer         getTxSent()          { return txSent; }
    public Integer         getTxApproved()      { return txApproved; }
    public Integer         getTxDeclined()      { return txDeclined; }
    public BigDecimal      getTpsActualAvg()    { return tpsActualAvg; }
    public BigDecimal      getResponseTimeAvg() { return responseTimeAvg; }
    public BigDecimal      getResponseTimeMin() { return responseTimeMin; }
    public BigDecimal      getResponseTimeMax() { return responseTimeMax; }
    public BigDecimal      getResponseTimeP95() { return responseTimeP95; }
    public BigDecimal      getResponseTimeP99() { return responseTimeP99; }
    public LocalDateTime   getStartedAt()       { return startedAt; }
    public LocalDateTime   getEndedAt()         { return endedAt; }
    public String          getReportDir()       { return reportDir; }
    public String          getReportPdf()       { return reportPdf; }
    public String          getReportExcel()     { return reportExcel; }

    // Setters
    public void setId(Long v)                   { this.id = v; }
    public void setUser(User v)                 { this.user = v; }
    public void setTest(Test v)                 { this.test = v; }
    public void setMode(ExecutionMode v)        { this.mode = v; }
    public void setStatus(ExecutionStatus v)    { this.status = v; }
    public void setTpsTarget(Integer v)         { this.tpsTarget = v; }
    public void setDurationSeconds(Integer v)   { this.durationSeconds = v; }
    public void setTxTotal(Integer v)           { this.txTotal = v; }
    public void setTxSent(Integer v)            { this.txSent = v; }
    public void setTxApproved(Integer v)        { this.txApproved = v; }
    public void setTxDeclined(Integer v)        { this.txDeclined = v; }
    public void setTpsActualAvg(BigDecimal v)   { this.tpsActualAvg = v; }
    public void setResponseTimeAvg(BigDecimal v){ this.responseTimeAvg = v; }
    public void setResponseTimeMin(BigDecimal v){ this.responseTimeMin = v; }
    public void setResponseTimeMax(BigDecimal v){ this.responseTimeMax = v; }
    public void setResponseTimeP95(BigDecimal v){ this.responseTimeP95 = v; }
    public void setResponseTimeP99(BigDecimal v){ this.responseTimeP99 = v; }
    public void setStartedAt(LocalDateTime v)   { this.startedAt = v; }
    public void setEndedAt(LocalDateTime v)     { this.endedAt = v; }
    public void setReportDir(String v)          { this.reportDir = v; }
    public void setReportPdf(String v)          { this.reportPdf = v; }
    public void setReportExcel(String v)        { this.reportExcel = v; }
}
