package com.staging.sg.common.dto;

public class TpsStepDto {
    private Long    id;
    private Integer stepOrder;
    private Integer startSeconds;
    private Integer endSeconds;
    private Integer tpsValue;

    public Long    getId()          { return id; }
    public Integer getStepOrder()   { return stepOrder; }
    public Integer getStartSeconds(){ return startSeconds; }
    public Integer getEndSeconds()  { return endSeconds; }
    public Integer getTpsValue()    { return tpsValue; }

    public void setId(Long v)           { this.id = v; }
    public void setStepOrder(Integer v) { this.stepOrder = v; }
    public void setStartSeconds(Integer v){ this.startSeconds = v; }
    public void setEndSeconds(Integer v){ this.endSeconds = v; }
    public void setTpsValue(Integer v)  { this.tpsValue = v; }
}
