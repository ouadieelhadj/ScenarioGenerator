package com.staging.sg.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "campaign_execution_de39_stats")
public class CampaignExecutionDe39Stat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false) private Long executionId;
    @Column(length = 3, nullable = false)            private String de39;
    @Column(nullable = false)                        private Integer count = 0;

    public CampaignExecutionDe39Stat() {}

    public Long getId()           { return id; }
    public Long getExecutionId()  { return executionId; }
    public String getDe39()       { return de39; }
    public Integer getCount()     { return count; }

    public void setId(Long v)          { this.id = v; }
    public void setExecutionId(Long v) { this.executionId = v; }
    public void setDe39(String v)      { this.de39 = v; }
    public void setCount(Integer v)    { this.count = v; }
}
