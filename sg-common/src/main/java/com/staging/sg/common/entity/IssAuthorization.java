package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "iss_authorizations")
public class IssAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Request 0100 reçu
    @Column(name = "de002_pan",         length = 20)  private String  de002Pan;
    @Column(name = "de003_proc_code",   length = 6)   private String  de003ProcCode;
    @Column(name = "de004_amount")                    private Long    de004Amount;
    @Column(name = "de007_datetime",    length = 10)  private String  de007Datetime;
    @Column(name = "de011_stan",        length = 6)   private String  de011Stan;
    @Column(name = "de012_local_time",  length = 6)   private String  de012LocalTime;
    @Column(name = "de013_local_date",  length = 4)   private String  de013LocalDate;
    @Column(name = "de018_mcc",         length = 4)   private String  de018Mcc;
    @Column(name = "de022_pos_mode",    length = 3)   private String  de022PosMode;
    @Column(name = "de032_acq_id",      length = 11)  private String  de032AcqId;
    @Column(name = "de037_rrn",         length = 12)  private String  de037Rrn;
    @Column(name = "de041_term_id",     length = 8)   private String  de041TermId;
    @Column(name = "de042_merch_id",    length = 15)  private String  de042MerchId;
    @Column(name = "de043_merch_name",  length = 40)  private String  de043MerchName;
    @Column(name = "de049_currency",    length = 3)   private String  de049Currency;
    @Column(name = "de052_pin_present")               private Boolean de052PinPresent = false;
    @Column(name = "mac_verified")                    private Boolean macVerified = false;

    // Response 0110 envoyé
    @Column(name = "de038_auth_code",   length = 6)   private String  de038AuthCode;
    @Column(name = "de039_response",    length = 2)   private String  de039Response;
    @Column(name = "decision_reason",   length = 100) private String  decisionReason;
    @Column(name = "approved")                        private Boolean approved;

    // Metrics
    @Column(name = "request_hex",  columnDefinition = "TEXT") private String requestHex;
    @Column(name = "response_hex", columnDefinition = "TEXT") private String responseHex;
    @Column(name = "received_at")                     private LocalDateTime receivedAt = LocalDateTime.now();
    @Column(name = "responded_at")                    private LocalDateTime respondedAt;

    @Column(name = "ipm_generated")              private Boolean ipmGenerated = false;
    @Column(name = "ipm_file_id")                private Long ipmFileId;
    @Column(name = "ipm_file_name", length = 100) private String ipmFileName;
    @Column(name = "ipm_generated_at")           private java.time.LocalDateTime ipmGeneratedAt;

    public Boolean getIpmGenerated()                 { return ipmGenerated; }
    public Long    getIpmFileId()                    { return ipmFileId; }
    public String  getIpmFileName()                  { return ipmFileName; }
    public java.time.LocalDateTime getIpmGeneratedAt() { return ipmGeneratedAt; }
    public void setIpmGenerated(Boolean v)           { this.ipmGenerated = v; }
    public void setIpmFileId(Long v)                 { this.ipmFileId = v; }
    public void setIpmFileName(String v)             { this.ipmFileName = v; }
    public void setIpmGeneratedAt(java.time.LocalDateTime v) { this.ipmGeneratedAt = v; }

    public IssAuthorization() {}

    // Getters
    public Long          getId()             { return id; }
    public String        getDe002Pan()       { return de002Pan; }
    public String        getDe003ProcCode()  { return de003ProcCode; }
    public Long          getDe004Amount()    { return de004Amount; }
    public String        getDe007Datetime()  { return de007Datetime; }
    public String        getDe011Stan()      { return de011Stan; }
    public String        getDe012LocalTime() { return de012LocalTime; }
    public String        getDe013LocalDate() { return de013LocalDate; }
    public String        getDe018Mcc()       { return de018Mcc; }
    public String        getDe022PosMode()   { return de022PosMode; }
    public String        getDe032AcqId()     { return de032AcqId; }
    public String        getDe037Rrn()       { return de037Rrn; }
    public String        getDe041TermId()    { return de041TermId; }
    public String        getDe042MerchId()   { return de042MerchId; }
    public String        getDe043MerchName() { return de043MerchName; }
    public String        getDe049Currency()  { return de049Currency; }
    public Boolean       getDe052PinPresent(){ return de052PinPresent; }
    public Boolean       getMacVerified()    { return macVerified; }
    public String        getDe038AuthCode()  { return de038AuthCode; }
    public String        getDe039Response()  { return de039Response; }
    public String        getDecisionReason() { return decisionReason; }
    public Boolean       getApproved()       { return approved; }
    public String        getRequestHex()     { return requestHex; }
    public String        getResponseHex()    { return responseHex; }
    public LocalDateTime getReceivedAt()     { return receivedAt; }
    public LocalDateTime getRespondedAt()    { return respondedAt; }

    // Setters
    public void setId(Long v)                  { this.id = v; }
    public void setDe002Pan(String v)          { this.de002Pan = v; }
    public void setDe003ProcCode(String v)     { this.de003ProcCode = v; }
    public void setDe004Amount(Long v)         { this.de004Amount = v; }
    public void setDe007Datetime(String v)     { this.de007Datetime = v; }
    public void setDe011Stan(String v)         { this.de011Stan = v; }
    public void setDe012LocalTime(String v)    { this.de012LocalTime = v; }
    public void setDe013LocalDate(String v)    { this.de013LocalDate = v; }
    public void setDe018Mcc(String v)          { this.de018Mcc = v; }
    public void setDe022PosMode(String v)      { this.de022PosMode = v; }
    public void setDe032AcqId(String v)        { this.de032AcqId = v; }
    public void setDe037Rrn(String v)          { this.de037Rrn = v; }
    public void setDe041TermId(String v)       { this.de041TermId = v; }
    public void setDe042MerchId(String v)      { this.de042MerchId = v; }
    public void setDe043MerchName(String v)    { this.de043MerchName = v; }
    public void setDe049Currency(String v)     { this.de049Currency = v; }
    public void setDe052PinPresent(Boolean v)  { this.de052PinPresent = v; }
    public void setMacVerified(Boolean v)      { this.macVerified = v; }
    public void setDe038AuthCode(String v)     { this.de038AuthCode = v; }
    public void setDe039Response(String v)     { this.de039Response = v; }
    public void setDecisionReason(String v)    { this.decisionReason = v; }
    public void setApproved(Boolean v)         { this.approved = v; }
    public void setRequestHex(String v)        { this.requestHex = v; }
    public void setResponseHex(String v)       { this.responseHex = v; }
    public void setReceivedAt(LocalDateTime v) { this.receivedAt = v; }
    public void setRespondedAt(LocalDateTime v){ this.respondedAt = v; }
}
