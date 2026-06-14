package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "results")
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "pan_masked", length = 20)
    private String panMasked;

    @Column(name = "de039", length = 2)
    private String de039;

    @Column(name = "de038_auth_code", length = 6)
    private String de038AuthCode;

    @Column(name = "approved")
    private Boolean approved;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "request_hex", columnDefinition = "TEXT")
    private String requestHex;

    @Column(name = "response_hex", columnDefinition = "TEXT")
    private String responseHex;

    @Column(name = "executed_at")
    private LocalDateTime executedAt = LocalDateTime.now();

    public Result() {}

    // Getters
    public Long          getId()          { return id; }
    public Execution     getExecution()   { return execution; }
    public String        getPanMasked()   { return panMasked; }
    public String        getDe039()       { return de039; }
    public String        getDe038AuthCode(){ return de038AuthCode; }
    public Boolean       getApproved()    { return approved; }
    public Integer       getDurationMs()  { return durationMs; }
    public String        getRequestHex()  { return requestHex; }
    public String        getResponseHex() { return responseHex; }
    public LocalDateTime getExecutedAt()  { return executedAt; }

    // Setters
    public void setId(Long v)                { this.id = v; }
    public void setExecution(Execution v)    { this.execution = v; }
    public void setPanMasked(String v)       { this.panMasked = v; }
    public void setDe039(String v)           { this.de039 = v; }
    public void setDe038AuthCode(String v)   { this.de038AuthCode = v; }
    public void setApproved(Boolean v)       { this.approved = v; }
    public void setDurationMs(Integer v)     { this.durationMs = v; }
    public void setRequestHex(String v)      { this.requestHex = v; }
    public void setResponseHex(String v)     { this.responseHex = v; }
    public void setExecutedAt(LocalDateTime v){ this.executedAt = v; }
}
