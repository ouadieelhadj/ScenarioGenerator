package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100) private String name;
    @Column(length = 255)                   private String description;
    @Column(length = 50)                    private String category;
    @Column(nullable = false, length = 20)  private String network = "DMAS";
    @Column(nullable = false, length = 20)  private String initiator = "ACQUIRER";
    @Column(columnDefinition = "TEXT")      private String config;
    @Column(name = "expected_de039", length = 2) private String expectedDe039;
    @Column(nullable = false)               private boolean active = true;
    @Column(name = "created_at")            private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "sla_p95_max_ms")        private Integer slaP95MaxMs;
    @Column(name = "sla_error_rate_max", precision = 5, scale = 2) private BigDecimal slaErrorRateMax;
    @Column(name = "sla_approval_min",   precision = 5, scale = 2) private BigDecimal slaApprovalMin;
    @Column(name = "stop_on_error_rate", precision = 5, scale = 2) private BigDecimal stopOnErrorRate;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<CampaignLoadStep> loadSteps = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getNetwork() { return network; }
    public void setNetwork(String v) { this.network = v; }
    public String getInitiator() { return initiator; }
    public void setInitiator(String v) { this.initiator = v; }
    public String getConfig() { return config; }
    public void setConfig(String v) { this.config = v; }
    public String getExpectedDe039() { return expectedDe039; }
    public void setExpectedDe039(String v) { this.expectedDe039 = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User v) { this.createdBy = v; }
    public Integer getSlaP95MaxMs() { return slaP95MaxMs; }
    public void setSlaP95MaxMs(Integer v) { this.slaP95MaxMs = v; }
    public BigDecimal getSlaErrorRateMax() { return slaErrorRateMax; }
    public void setSlaErrorRateMax(BigDecimal v) { this.slaErrorRateMax = v; }
    public BigDecimal getSlaApprovalMin() { return slaApprovalMin; }
    public void setSlaApprovalMin(BigDecimal v) { this.slaApprovalMin = v; }
    public BigDecimal getStopOnErrorRate() { return stopOnErrorRate; }
    public void setStopOnErrorRate(BigDecimal v) { this.stopOnErrorRate = v; }
    public List<CampaignLoadStep> getLoadSteps() { return loadSteps; }
    public void setLoadSteps(List<CampaignLoadStep> v) { this.loadSteps = v; }
}
