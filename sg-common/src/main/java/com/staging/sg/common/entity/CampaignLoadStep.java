package com.staging.sg.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "campaign_load_steps")
public class CampaignLoadStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "step_order", nullable = false)    private Integer stepOrder;
    @Column(name = "start_seconds", nullable = false) private Integer startSeconds;
    @Column(name = "end_seconds", nullable = false)   private Integer endSeconds;
    @Column(name = "tps_value", nullable = false)     private Integer tpsValue;
    @Column(name = "concurrency")                     private Integer concurrency;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign v) { this.campaign = v; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }
    public Integer getStartSeconds() { return startSeconds; }
    public void setStartSeconds(Integer v) { this.startSeconds = v; }
    public Integer getEndSeconds() { return endSeconds; }
    public void setEndSeconds(Integer v) { this.endSeconds = v; }
    public Integer getTpsValue() { return tpsValue; }
    public void setTpsValue(Integer v) { this.tpsValue = v; }
    public Integer getConcurrency() { return concurrency; }
    public void setConcurrency(Integer v) { this.concurrency = v; }
}
