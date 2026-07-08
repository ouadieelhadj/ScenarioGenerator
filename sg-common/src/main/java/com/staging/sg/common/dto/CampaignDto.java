package com.staging.sg.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Representation de sortie d'une campagne (avec ses paliers). */
public class CampaignDto {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String network;
    private String initiator;
    private String config;
    private String expectedDe039;
    private boolean active;
    private LocalDateTime createdAt;
    private String createdByLogin;

    private Integer    slaP95MaxMs;
    private BigDecimal slaErrorRateMax;
    private BigDecimal slaApprovalMin;
    private BigDecimal stopOnErrorRate;

    private List<LoadStepDto> loadSteps = new ArrayList<>();

    public static class LoadStepDto {
        private Long id;
        private Integer stepOrder;
        private Integer startSeconds;
        private Integer endSeconds;
        private Integer tpsValue;
        private Integer concurrency;

        public Long getId() { return id; }
        public void setId(Long v) { this.id = v; }
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
    public String getCreatedByLogin() { return createdByLogin; }
    public void setCreatedByLogin(String v) { this.createdByLogin = v; }
    public Integer getSlaP95MaxMs() { return slaP95MaxMs; }
    public void setSlaP95MaxMs(Integer v) { this.slaP95MaxMs = v; }
    public BigDecimal getSlaErrorRateMax() { return slaErrorRateMax; }
    public void setSlaErrorRateMax(BigDecimal v) { this.slaErrorRateMax = v; }
    public BigDecimal getSlaApprovalMin() { return slaApprovalMin; }
    public void setSlaApprovalMin(BigDecimal v) { this.slaApprovalMin = v; }
    public BigDecimal getStopOnErrorRate() { return stopOnErrorRate; }
    public void setStopOnErrorRate(BigDecimal v) { this.stopOnErrorRate = v; }
    public List<LoadStepDto> getLoadSteps() { return loadSteps; }
    public void setLoadSteps(List<LoadStepDto> v) { this.loadSteps = v; }
}
