package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_execution_results")
public class CampaignExecutionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false) private Long executionId;
    @Column(name = "pan_masked", length = 20)        private String panMasked;
    @Column(length = 3)                              private String de39;
    @Column                                          private Boolean approved;
    @Column(name = "duration_ms")                    private Integer durationMs;
    @Column(name = "executed_at")                    private LocalDateTime executedAt = LocalDateTime.now();

    public CampaignExecutionResult() {}

    public Long getId()             { return id; }
    public Long getExecutionId()    { return executionId; }
    public String getPanMasked()    { return panMasked; }
    public String getDe39()         { return de39; }
    public Boolean getApproved()    { return approved; }
    public Integer getDurationMs()  { return durationMs; }
    public LocalDateTime getExecutedAt() { return executedAt; }

    public void setId(Long v)          { this.id = v; }
    public void setExecutionId(Long v) { this.executionId = v; }
    public void setPanMasked(String v) { this.panMasked = v; }
    public void setDe39(String v)      { this.de39 = v; }
    public void setApproved(Boolean v) { this.approved = v; }
    public void setDurationMs(Integer v){ this.durationMs = v; }
    public void setExecutedAt(LocalDateTime v) { this.executedAt = v; }
}
