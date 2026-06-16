package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipm_records")
public class IpmRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ipm_file_id", nullable = false)
    private IpmFile ipmFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acq_auth_id")
    private AcqAuthorization acqAuthorization;

    @Column(name = "message_number")                      private Integer messageNumber;
    @Column(name = "record_type",    length = 15)         private String  recordType;
    @Column(name = "mti",            length = 4)          private String  mti;
    @Column(name = "function_code",  length = 3)          private String  functionCode;
    @Column(name = "de002_pan",      length = 20)         private String  de002Pan;
    @Column(name = "de003_proc_code",length = 6)          private String  de003ProcCode;
    @Column(name = "de004_amount")                        private Long    de004Amount;
    @Column(name = "de012_local_dt", length = 12)         private String  de012LocalDt;
    @Column(name = "de022_pos_code", length = 12)         private String  de022PosCode;
    @Column(name = "de024_func_code",length = 3)          private String  de024FuncCode;
    @Column(name = "de025_reason",   length = 4)          private String  de025Reason;
    @Column(name = "de026_mcc",      length = 4)          private String  de026Mcc;
    @Column(name = "de032_acq_id",   length = 11)         private String  de032AcqId;
    @Column(name = "de037_rrn",      length = 12)         private String  de037Rrn;
    @Column(name = "de038_auth_code",length = 6)          private String  de038AuthCode;
    @Column(name = "de041_term_id",  length = 8)          private String  de041TermId;
    @Column(name = "de042_merch_id", length = 15)         private String  de042MerchId;
    @Column(name = "de043_merch_name",length = 40)        private String  de043MerchName;
    @Column(name = "de049_currency", length = 3)          private String  de049Currency;
    @Column(name = "de071_msg_num",  length = 8)          private String  de071MsgNum;
    @Column(name = "de005_amount_recon")                 private Long    de005AmountRecon;
    @Column(name = "de031_acq_ref_data", length = 23)    private String  de031AcqRefData;
    @Column(name = "de050_currency_recon", length = 3)   private String  de050CurrencyRecon;
    @Column(name = "de063_network_data", length = 50)    private String  de063NetworkData;
    @Column(name = "raw_hex",        columnDefinition = "TEXT") private String rawHex;
    @Column(name = "raw_ascii",      columnDefinition = "TEXT") private String rawAscii;
    @Column(name = "status",         length = 10)         private String  status;
    @Column(name = "error_message",  length = 255)        private String  errorMessage;
    @Column(name = "created_at")                          private LocalDateTime createdAt;

    public IpmRecord() {
        this.createdAt = LocalDateTime.now();
        this.status    = "OK";
    }

    // Getters
    public Long             getId()            { return id; }
    public IpmFile          getIpmFile()       { return ipmFile; }
    public AcqAuthorization getAcqAuth()       { return acqAuthorization; }
    public Integer          getMessageNumber() { return messageNumber; }
    public String           getRecordType()    { return recordType; }
    public String           getMti()           { return mti; }
    public String           getFunctionCode()  { return functionCode; }
    public String           getDe002Pan()      { return de002Pan; }
    public String           getDe003ProcCode() { return de003ProcCode; }
    public Long             getDe004Amount()   { return de004Amount; }
    public String           getDe012LocalDt()  { return de012LocalDt; }
    public String           getDe022PosCode()  { return de022PosCode; }
    public String           getDe024FuncCode() { return de024FuncCode; }
    public String           getDe025Reason()   { return de025Reason; }
    public String           getDe026Mcc()      { return de026Mcc; }
    public String           getDe032AcqId()    { return de032AcqId; }
    public String           getDe037Rrn()      { return de037Rrn; }
    public String           getDe038AuthCode() { return de038AuthCode; }
    public String           getDe041TermId()   { return de041TermId; }
    public String           getDe042MerchId()  { return de042MerchId; }
    public String           getDe043MerchName(){ return de043MerchName; }
    public String           getDe049Currency() { return de049Currency; }
    public String           getDe071MsgNum()   { return de071MsgNum; }
    public Long             getDe005AmountRecon()   { return de005AmountRecon; }
    public String           getDe031AcqRefData()    { return de031AcqRefData; }
    public String           getDe050CurrencyRecon() { return de050CurrencyRecon; }
    public String           getDe063NetworkData()   { return de063NetworkData; }
    public String           getRawHex()        { return rawHex; }
    public String           getRawAscii()      { return rawAscii; }
    public String           getStatus()        { return status; }
    public String           getErrorMessage()  { return errorMessage; }
    public LocalDateTime    getCreatedAt()     { return createdAt; }

    // Setters
    public void setId(Long v)                       { this.id = v; }
    public void setIpmFile(IpmFile v)               { this.ipmFile = v; }
    public void setAcqAuth(AcqAuthorization v)      { this.acqAuthorization = v; }
    public void setMessageNumber(Integer v)         { this.messageNumber = v; }
    public void setRecordType(String v)             { this.recordType = v; }
    public void setMti(String v)                    { this.mti = v; }
    public void setFunctionCode(String v)           { this.functionCode = v; }
    public void setDe002Pan(String v)               { this.de002Pan = v; }
    public void setDe003ProcCode(String v)          { this.de003ProcCode = v; }
    public void setDe004Amount(Long v)              { this.de004Amount = v; }
    public void setDe012LocalDt(String v)           { this.de012LocalDt = v; }
    public void setDe022PosCode(String v)           { this.de022PosCode = v; }
    public void setDe024FuncCode(String v)          { this.de024FuncCode = v; }
    public void setDe025Reason(String v)            { this.de025Reason = v; }
    public void setDe026Mcc(String v)               { this.de026Mcc = v; }
    public void setDe032AcqId(String v)             { this.de032AcqId = v; }
    public void setDe037Rrn(String v)               { this.de037Rrn = v; }
    public void setDe038AuthCode(String v)          { this.de038AuthCode = v; }
    public void setDe041TermId(String v)            { this.de041TermId = v; }
    public void setDe042MerchId(String v)           { this.de042MerchId = v; }
    public void setDe043MerchName(String v)         { this.de043MerchName = v; }
    public void setDe049Currency(String v)          { this.de049Currency = v; }
    public void setDe071MsgNum(String v)            { this.de071MsgNum = v; }
    public void setDe005AmountRecon(Long v)         { this.de005AmountRecon = v; }
    public void setDe031AcqRefData(String v)        { this.de031AcqRefData = v; }
    public void setDe050CurrencyRecon(String v)     { this.de050CurrencyRecon = v; }
    public void setDe063NetworkData(String v)       { this.de063NetworkData = v; }
    public void setRawHex(String v)                 { this.rawHex = v; }
    public void setRawAscii(String v)               { this.rawAscii = v; }
    public void setStatus(String v)                 { this.status = v; }
    public void setErrorMessage(String v)           { this.errorMessage = v; }
    public void setCreatedAt(LocalDateTime v)       { this.createdAt = v; }
}
