package com.staging.sg.acquirer.acquirer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McAdviceRequest {

    @JsonProperty("DE002_PAN") private String DE002_PAN;
    @JsonProperty("DE004_AMOUNT") private Long DE004_AMOUNT;
    @JsonProperty("DE003_PROCESSING_CODE") private String DE003_PROCESSING_CODE;
    @JsonProperty("DE018_MCC") private String DE018_MCC;
    @JsonProperty("DE022_POS_ENTRY_MODE") private String DE022_POS_ENTRY_MODE;
    @JsonProperty("DE049_CURRENCY_CODE") private String DE049_CURRENCY_CODE;
    @JsonProperty("DE037_RETRIEVAL_REF") private String DE037_RETRIEVAL_REF;
    @JsonProperty("DE038_AUTH_CODE") private String DE038_AUTH_CODE;
    @JsonProperty("DE039_RESPONSE_CODE") private String DE039_RESPONSE_CODE;
    @JsonProperty("DE060_ADVICE_REASON") private String DE060_ADVICE_REASON;  // 191=Acquirer Completed
    private Long   executionId;

    public McAdviceRequest() {}

    // Getters
    public String getDE002_PAN()            { return DE002_PAN; }
    public Long   getDE004_AMOUNT()         { return DE004_AMOUNT; }
    public String getDE003_PROCESSING_CODE(){ return DE003_PROCESSING_CODE; }
    public String getDE018_MCC()            { return DE018_MCC; }
    public String getDE022_POS_ENTRY_MODE() { return DE022_POS_ENTRY_MODE; }
    public String getDE049_CURRENCY_CODE()  { return DE049_CURRENCY_CODE; }
    public String getDE037_RETRIEVAL_REF()  { return DE037_RETRIEVAL_REF; }
    public String getDE038_AUTH_CODE()      { return DE038_AUTH_CODE; }
    public String getDE039_RESPONSE_CODE()  { return DE039_RESPONSE_CODE; }
    public String getDE060_ADVICE_REASON()  { return DE060_ADVICE_REASON; }
    public Long   getExecutionId()          { return executionId; }

    // Setters
    public void setDE002_PAN(String v)            { this.DE002_PAN = v; }
    public void setDE004_AMOUNT(Long v)           { this.DE004_AMOUNT = v; }
    public void setDE003_PROCESSING_CODE(String v){ this.DE003_PROCESSING_CODE = v; }
    public void setDE018_MCC(String v)            { this.DE018_MCC = v; }
    public void setDE022_POS_ENTRY_MODE(String v) { this.DE022_POS_ENTRY_MODE = v; }
    public void setDE049_CURRENCY_CODE(String v)  { this.DE049_CURRENCY_CODE = v; }
    public void setDE037_RETRIEVAL_REF(String v)  { this.DE037_RETRIEVAL_REF = v; }
    public void setDE038_AUTH_CODE(String v)      { this.DE038_AUTH_CODE = v; }
    public void setDE039_RESPONSE_CODE(String v)  { this.DE039_RESPONSE_CODE = v; }
    public void setDE060_ADVICE_REASON(String v)  { this.DE060_ADVICE_REASON = v; }
    public void setExecutionId(Long v)      { this.executionId = v; }
}
