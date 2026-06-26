package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_execution_results")
public class CampaignExecutionResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private CampaignExecution execution;

    @Column(name = "step_order")   private Integer stepOrder;
    @Column(name = "pan_masked", length = 20) private String panMasked;
    @Column(length = 2)            private String de039;
    @Column(name = "de038_auth_code", length = 6) private String de038AuthCode;
    @Column                        private Boolean approved;
    @Column(name = "duration_ms")  private Integer durationMs;
    @Column(name = "request_hex",  columnDefinition = "TEXT") private String requestHex;
    @Column(name = "response_hex", columnDefinition = "TEXT") private String responseHex;
    @Column(name = "executed_at")  private LocalDateTime executedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public CampaignExecution getExecution() { return execution; }
    public void setExecution(CampaignExecution v) { this.execution = v; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }
    public String getPanMasked() { return panMasked; }
    public void setPanMasked(String v) { this.panMasked = v; }
    public String getDe039() { return de039; }
    public void setDe039(String v) { this.de039 = v; }
    public String getDe038AuthCode() { return de038AuthCode; }
    public void setDe038AuthCode(String v) { this.de038AuthCode = v; }
    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean v) { this.approved = v; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer v) { this.durationMs = v; }
    public String getRequestHex() { return requestHex; }
    public void setRequestHex(String v) { this.requestHex = v; }
    public String getResponseHex() { return responseHex; }
    public void setResponseHex(String v) { this.responseHex = v; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime v) { this.executedAt = v; }
}
