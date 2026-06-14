package com.staging.sg.common.dto;

import com.staging.sg.common.entity.TpsStep;
import java.util.List;

public class TestDto {
    private Long          id;
    private String        name;
    private String        description;
    private String        category;
    private Long          messageTypeId;
    private String        messageTypeName;
    private String        config;
    private String        expectedDe039;
    private boolean       active;
    private List<TpsStepDto> tpsSteps;

    public Long    getId()             { return id; }
    public String  getName()           { return name; }
    public String  getDescription()    { return description; }
    public String  getCategory()       { return category; }
    public Long    getMessageTypeId()  { return messageTypeId; }
    public String  getMessageTypeName(){ return messageTypeName; }
    public String  getConfig()         { return config; }
    public String  getExpectedDe039()  { return expectedDe039; }
    public boolean isActive()          { return active; }
    public List<TpsStepDto> getTpsSteps() { return tpsSteps; }

    public void setId(Long v)              { this.id = v; }
    public void setName(String v)          { this.name = v; }
    public void setDescription(String v)   { this.description = v; }
    public void setCategory(String v)      { this.category = v; }
    public void setMessageTypeId(Long v)   { this.messageTypeId = v; }
    public void setMessageTypeName(String v){ this.messageTypeName = v; }
    public void setConfig(String v)        { this.config = v; }
    public void setExpectedDe039(String v) { this.expectedDe039 = v; }
    public void setActive(boolean v)       { this.active = v; }
    public void setTpsSteps(List<TpsStepDto> v) { this.tpsSteps = v; }
}
