package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "acq_reversals")
public class AcqReversal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acq_auth_id")
    private AcqAuthorization acqAuthorization;

    // Request 0400
    @Column(name = "de002_pan",        length = 20)  private String  de002Pan;
    @Column(name = "de003_proc_code",  length = 6)   private String  de003ProcCode;
    @Column(name = "de004_amount")                   private Long    de004Amount;
    @Column(name = "de007_datetime",   length = 10)  private String  de007Datetime;
    @Column(name = "de011_stan",       length = 6)   private String  de011Stan;
    @Column(name = "de037_rrn",        length = 12)  private String  de037Rrn;
    @Column(name = "de038_auth_code",  length = 6)   private String  de038AuthCode;
    @Column(name = "de039_original",   length = 2)   private String  de039Original;
    @Column(name = "de041_term_id",    length = 8)   private String  de041TermId;
    @Column(name = "de042_merch_id",   length = 15)  private String  de042MerchId;
    @Column(name = "de049_currency",   length = 3)   private String  de049Currency;
    @Column(name = "de056_orig_data",  length = 40)  private String  de056OrigData;

    // Response 0410
    @Column(name = "de039_response",   length = 2)   private String  de039Response;
    @Column(name = "reversed")                       private Boolean reversed;

    // Metrics
    @Column(name = "duration_ms")                    private Integer durationMs;
    @Column(name = "request_hex",  columnDefinition = "TEXT") private String requestHex;
    @Column(name = "response_hex", columnDefinition = "TEXT") private String responseHex;
    @Column(name = "sent_at")                        private LocalDateTime sentAt = LocalDateTime.now();

    public AcqReversal() {}

    // Getters
    public Long             getId()              { return id; }
    public Execution        getExecution()       { return execution; }
    public AcqAuthorization getAcqAuthorization(){ return acqAuthorization; }
    public String           getDe002Pan()        { return de002Pan; }
    public String           getDe003ProcCode()   { return de003ProcCode; }
    public Long             getDe004Amount()     { return de004Amount; }
    public String           getDe007Datetime()   { return de007Datetime; }
    public String           getDe011Stan()       { return de011Stan; }
    public String           getDe037Rrn()        { return de037Rrn; }
    public String           getDe038AuthCode()   { return de038AuthCode; }
    public String           getDe039Original()   { return de039Original; }
    public String           getDe041TermId()     { return de041TermId; }
    public String           getDe042MerchId()    { return de042MerchId; }
    public String           getDe049Currency()   { return de049Currency; }
    public String           getDe056OrigData()   { return de056OrigData; }
    public String           getDe039Response()   { return de039Response; }
    public Boolean          getReversed()        { return reversed; }
    public Integer          getDurationMs()      { return durationMs; }
    public String           getRequestHex()      { return requestHex; }
    public String           getResponseHex()     { return responseHex; }
    public LocalDateTime    getSentAt()          { return sentAt; }

    // Setters
    public void setId(Long v)                         { this.id = v; }
    public void setExecution(Execution v)             { this.execution = v; }
    public void setAcqAuthorization(AcqAuthorization v){ this.acqAuthorization = v; }
    public void setDe002Pan(String v)                 { this.de002Pan = v; }
    public void setDe003ProcCode(String v)            { this.de003ProcCode = v; }
    public void setDe004Amount(Long v)                { this.de004Amount = v; }
    public void setDe007Datetime(String v)            { this.de007Datetime = v; }
    public void setDe011Stan(String v)                { this.de011Stan = v; }
    public void setDe037Rrn(String v)                 { this.de037Rrn = v; }
    public void setDe038AuthCode(String v)            { this.de038AuthCode = v; }
    public void setDe039Original(String v)            { this.de039Original = v; }
    public void setDe041TermId(String v)              { this.de041TermId = v; }
    public void setDe042MerchId(String v)             { this.de042MerchId = v; }
    public void setDe049Currency(String v)            { this.de049Currency = v; }
    public void setDe056OrigData(String v)            { this.de056OrigData = v; }
    public void setDe039Response(String v)            { this.de039Response = v; }
    public void setReversed(Boolean v)                { this.reversed = v; }
    public void setDurationMs(Integer v)              { this.durationMs = v; }
    public void setRequestHex(String v)               { this.requestHex = v; }
    public void setResponseHex(String v)              { this.responseHex = v; }
    public void setSentAt(LocalDateTime v)            { this.sentAt = v; }
}
