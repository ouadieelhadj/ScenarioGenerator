package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "acq_advices")
public class AcqAdvice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acq_auth_id")
    private AcqAuthorization acqAuthorization;

    // Request 0120
    @Column(name = "de002_pan",            length = 20) private String  de002Pan;
    @Column(name = "de003_proc_code",      length = 6)  private String  de003ProcCode;
    @Column(name = "de004_amount")                      private Long    de004Amount;
    @Column(name = "de007_datetime",       length = 10) private String  de007Datetime;
    @Column(name = "de011_stan",           length = 6)  private String  de011Stan;
    @Column(name = "de037_rrn",            length = 12) private String  de037Rrn;
    @Column(name = "de038_auth_code",      length = 6)  private String  de038AuthCode;
    @Column(name = "de039_response",       length = 2)  private String  de039Response;
    @Column(name = "de049_currency",       length = 3)  private String  de049Currency;
    @Column(name = "de060_reason",         length = 3)  private String  de060Reason;

    // Response 0130
    @Column(name = "de039_advice_response", length = 2) private String  de039AdviceResponse;
    @Column(name = "accepted")                          private Boolean accepted;

    // Metrics
    @Column(name = "duration_ms")                       private Integer durationMs;
    @Column(name = "request_hex",  columnDefinition = "TEXT") private String requestHex;
    @Column(name = "response_hex", columnDefinition = "TEXT") private String responseHex;
    @Column(name = "sent_at")                           private LocalDateTime sentAt = LocalDateTime.now();

    public AcqAdvice() {}

    // Getters
    public Long             getId()                 { return id; }
    public Execution        getExecution()          { return execution; }
    public AcqAuthorization getAcqAuthorization()   { return acqAuthorization; }
    public String           getDe002Pan()           { return de002Pan; }
    public String           getDe003ProcCode()      { return de003ProcCode; }
    public Long             getDe004Amount()        { return de004Amount; }
    public String           getDe007Datetime()      { return de007Datetime; }
    public String           getDe011Stan()          { return de011Stan; }
    public String           getDe037Rrn()           { return de037Rrn; }
    public String           getDe038AuthCode()      { return de038AuthCode; }
    public String           getDe039Response()      { return de039Response; }
    public String           getDe049Currency()      { return de049Currency; }
    public String           getDe060Reason()        { return de060Reason; }
    public String           getDe039AdviceResponse(){ return de039AdviceResponse; }
    public Boolean          getAccepted()           { return accepted; }
    public Integer          getDurationMs()         { return durationMs; }
    public String           getRequestHex()         { return requestHex; }
    public String           getResponseHex()        { return responseHex; }
    public LocalDateTime    getSentAt()             { return sentAt; }

    // Setters
    public void setId(Long v)                          { this.id = v; }
    public void setExecution(Execution v)              { this.execution = v; }
    public void setAcqAuthorization(AcqAuthorization v){ this.acqAuthorization = v; }
    public void setDe002Pan(String v)                  { this.de002Pan = v; }
    public void setDe003ProcCode(String v)             { this.de003ProcCode = v; }
    public void setDe004Amount(Long v)                 { this.de004Amount = v; }
    public void setDe007Datetime(String v)             { this.de007Datetime = v; }
    public void setDe011Stan(String v)                 { this.de011Stan = v; }
    public void setDe037Rrn(String v)                  { this.de037Rrn = v; }
    public void setDe038AuthCode(String v)             { this.de038AuthCode = v; }
    public void setDe039Response(String v)             { this.de039Response = v; }
    public void setDe049Currency(String v)             { this.de049Currency = v; }
    public void setDe060Reason(String v)               { this.de060Reason = v; }
    public void setDe039AdviceResponse(String v)       { this.de039AdviceResponse = v; }
    public void setAccepted(Boolean v)                 { this.accepted = v; }
    public void setDurationMs(Integer v)               { this.durationMs = v; }
    public void setRequestHex(String v)                { this.requestHex = v; }
    public void setResponseHex(String v)               { this.responseHex = v; }
    public void setSentAt(LocalDateTime v)             { this.sentAt = v; }
}
