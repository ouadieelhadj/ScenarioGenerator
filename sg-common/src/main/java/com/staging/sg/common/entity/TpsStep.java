package com.staging.sg.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tps_steps")
public class TpsStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private Test test;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "start_seconds", nullable = false)
    private Integer startSeconds;

    @Column(name = "end_seconds", nullable = false)
    private Integer endSeconds;

    @Column(name = "tps_value", nullable = false)
    private Integer tpsValue;

    public TpsStep() {}

    // Getters
    public Long    getId()          { return id; }
    public Test    getTest()        { return test; }
    public Integer getStepOrder()   { return stepOrder; }
    public Integer getStartSeconds(){ return startSeconds; }
    public Integer getEndSeconds()  { return endSeconds; }
    public Integer getTpsValue()    { return tpsValue; }

    // Setters
    public void setId(Long v)           { this.id = v; }
    public void setTest(Test v)         { this.test = v; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }
    public void setStartSeconds(Integer v){ this.startSeconds = v; }
    public void setEndSeconds(Integer v){ this.endSeconds = v; }
    public void setTpsValue(Integer v)  { this.tpsValue = v; }
}
