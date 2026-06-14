package com.staging.sg.acquirer.acquirer;

import java.util.Map;

/**
 * Result of Mastercard 0100/0110 authorization.
 * Contains all ISO 8583 fields from request and response.
 */
public class McAuthResult {

    // Context
    private String  mode;
    private long    seed;
    private String  host;
    private int     port;
    private boolean approved;
    private String  responseLabel;

    // 0100 Request fields
    private String DE002_PAN;
    private String DE003_PROCESSING_CODE;
    private String DE004_AMOUNT;
    private String DE007_TRANSMISSION_DATE_TIME;  // AUTO
    private String DE011_STAN;                    // AUTO
    private String DE012_LOCAL_TIME;              // AUTO
    private String DE013_LOCAL_DATE;              // AUTO
    private String DE018_MCC;
    private String DE022_POS_ENTRY_MODE;
    private String DE025_POS_CONDITION_CODE;
    private String DE032_ACQUIRING_BIN;
    private String DE033_FORWARDING_BIN;
    private String DE037_RRN;                     // AUTO
    private String DE041_TERMINAL_ID;
    private String DE042_MERCHANT_ID;
    private String DE043_MERCHANT_NAME;
    private String DE049_CURRENCY_CODE;
    private String DE052_PIN_BLOCK;               // PIN chiffré
    private String DE064_MAC;                     // MAC calculé

    // 0110 Response fields
    private String DE038_AUTH_CODE;
    private String DE039_RESPONSE_CODE;

    // Hex
    private String requestHex;
    private String responseHex;

    // All fields as map
    private Map<String, String> requestFields;
    private Map<String, String> responseFields;

    public McAuthResult() {}

    public boolean isApproved() { return approved; }

    // Getters
    public String  getMode()                         { return mode; }
    public long    getSeed()                         { return seed; }
    public String  getHost()                         { return host; }
    public int     getPort()                         { return port; }
    public boolean getApproved()                     { return approved; }
    public String  getResponseLabel()                { return responseLabel; }
    public String  getDE002_PAN()                    { return DE002_PAN; }
    public String  getDE003_PROCESSING_CODE()        { return DE003_PROCESSING_CODE; }
    public String  getDE004_AMOUNT()                 { return DE004_AMOUNT; }
    public String  getDE007_TRANSMISSION_DATE_TIME() { return DE007_TRANSMISSION_DATE_TIME; }
    public String  getDE011_STAN()                   { return DE011_STAN; }
    public String  getDE012_LOCAL_TIME()             { return DE012_LOCAL_TIME; }
    public String  getDE013_LOCAL_DATE()             { return DE013_LOCAL_DATE; }
    public String  getDE018_MCC()                    { return DE018_MCC; }
    public String  getDE022_POS_ENTRY_MODE()         { return DE022_POS_ENTRY_MODE; }
    public String  getDE025_POS_CONDITION_CODE()     { return DE025_POS_CONDITION_CODE; }
    public String  getDE032_ACQUIRING_BIN()          { return DE032_ACQUIRING_BIN; }
    public String  getDE033_FORWARDING_BIN()         { return DE033_FORWARDING_BIN; }
    public String  getDE037_RRN()                    { return DE037_RRN; }
    public String  getDE038_AUTH_CODE()              { return DE038_AUTH_CODE; }
    public String  getDE039_RESPONSE_CODE()          { return DE039_RESPONSE_CODE; }
    public String  getDE041_TERMINAL_ID()            { return DE041_TERMINAL_ID; }
    public String  getDE042_MERCHANT_ID()            { return DE042_MERCHANT_ID; }
    public String  getDE043_MERCHANT_NAME()          { return DE043_MERCHANT_NAME; }
    public String  getDE049_CURRENCY_CODE()          { return DE049_CURRENCY_CODE; }
    public String  getDE052_PIN_BLOCK()              { return DE052_PIN_BLOCK; }
    public String  getDE064_MAC()                    { return DE064_MAC; }
    public String  getRequestHex()                   { return requestHex; }
    public String  getResponseHex()                  { return responseHex; }
    public Map<String, String> getRequestFields()    { return requestFields; }
    public Map<String, String> getResponseFields()   { return responseFields; }

    // Setters
    public void setMode(String v)                         { this.mode = v; }
    public void setSeed(long v)                           { this.seed = v; }
    public void setHost(String v)                         { this.host = v; }
    public void setPort(int v)                            { this.port = v; }
    public void setApproved(boolean v)                    { this.approved = v; }
    public void setResponseLabel(String v)                { this.responseLabel = v; }
    public void setDE002_PAN(String v)                    { this.DE002_PAN = v; }
    public void setDE003_PROCESSING_CODE(String v)        { this.DE003_PROCESSING_CODE = v; }
    public void setDE004_AMOUNT(String v)                 { this.DE004_AMOUNT = v; }
    public void setDE007_TRANSMISSION_DATE_TIME(String v) { this.DE007_TRANSMISSION_DATE_TIME = v; }
    public void setDE011_STAN(String v)                   { this.DE011_STAN = v; }
    public void setDE012_LOCAL_TIME(String v)             { this.DE012_LOCAL_TIME = v; }
    public void setDE013_LOCAL_DATE(String v)             { this.DE013_LOCAL_DATE = v; }
    public void setDE018_MCC(String v)                    { this.DE018_MCC = v; }
    public void setDE022_POS_ENTRY_MODE(String v)         { this.DE022_POS_ENTRY_MODE = v; }
    public void setDE025_POS_CONDITION_CODE(String v)     { this.DE025_POS_CONDITION_CODE = v; }
    public void setDE032_ACQUIRING_BIN(String v)          { this.DE032_ACQUIRING_BIN = v; }
    public void setDE033_FORWARDING_BIN(String v)         { this.DE033_FORWARDING_BIN = v; }
    public void setDE037_RRN(String v)                    { this.DE037_RRN = v; }
    public void setDE038_AUTH_CODE(String v)              { this.DE038_AUTH_CODE = v; }
    public void setDE039_RESPONSE_CODE(String v)          { this.DE039_RESPONSE_CODE = v; }
    public void setDE041_TERMINAL_ID(String v)            { this.DE041_TERMINAL_ID = v; }
    public void setDE042_MERCHANT_ID(String v)            { this.DE042_MERCHANT_ID = v; }
    public void setDE043_MERCHANT_NAME(String v)          { this.DE043_MERCHANT_NAME = v; }
    public void setDE049_CURRENCY_CODE(String v)          { this.DE049_CURRENCY_CODE = v; }
    public void setDE052_PIN_BLOCK(String v)              { this.DE052_PIN_BLOCK = v; }
    public void setDE064_MAC(String v)                    { this.DE064_MAC = v; }
    public void setRequestHex(String v)                   { this.requestHex = v; }
    public void setResponseHex(String v)                  { this.responseHex = v; }
    public void setRequestFields(Map<String, String> v)   { this.requestFields = v; }
    public void setResponseFields(Map<String, String> v)  { this.responseFields = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final McAuthResult r = new McAuthResult();
        public Builder mode(String v)                         { r.mode = v;                         return this; }
        public Builder seed(long v)                           { r.seed = v;                         return this; }
        public Builder host(String v)                         { r.host = v;                         return this; }
        public Builder port(int v)                            { r.port = v;                         return this; }
        public Builder approved(boolean v)                    { r.approved = v;                     return this; }
        public Builder responseLabel(String v)                { r.responseLabel = v;                return this; }
        public Builder DE002_PAN(String v)                    { r.DE002_PAN = v;                    return this; }
        public Builder DE003_PROCESSING_CODE(String v)        { r.DE003_PROCESSING_CODE = v;        return this; }
        public Builder DE004_AMOUNT(String v)                 { r.DE004_AMOUNT = v;                 return this; }
        public Builder DE007_TRANSMISSION_DATE_TIME(String v) { r.DE007_TRANSMISSION_DATE_TIME = v; return this; }
        public Builder DE011_STAN(String v)                   { r.DE011_STAN = v;                   return this; }
        public Builder DE012_LOCAL_TIME(String v)             { r.DE012_LOCAL_TIME = v;             return this; }
        public Builder DE013_LOCAL_DATE(String v)             { r.DE013_LOCAL_DATE = v;             return this; }
        public Builder DE018_MCC(String v)                    { r.DE018_MCC = v;                    return this; }
        public Builder DE022_POS_ENTRY_MODE(String v)         { r.DE022_POS_ENTRY_MODE = v;         return this; }
        public Builder DE025_POS_CONDITION_CODE(String v)     { r.DE025_POS_CONDITION_CODE = v;     return this; }
        public Builder DE032_ACQUIRING_BIN(String v)          { r.DE032_ACQUIRING_BIN = v;          return this; }
        public Builder DE033_FORWARDING_BIN(String v)         { r.DE033_FORWARDING_BIN = v;         return this; }
        public Builder DE037_RRN(String v)                    { r.DE037_RRN = v;                    return this; }
        public Builder DE038_AUTH_CODE(String v)              { r.DE038_AUTH_CODE = v;              return this; }
        public Builder DE039_RESPONSE_CODE(String v)          { r.DE039_RESPONSE_CODE = v;          return this; }
        public Builder DE041_TERMINAL_ID(String v)            { r.DE041_TERMINAL_ID = v;            return this; }
        public Builder DE042_MERCHANT_ID(String v)            { r.DE042_MERCHANT_ID = v;            return this; }
        public Builder DE043_MERCHANT_NAME(String v)          { r.DE043_MERCHANT_NAME = v;          return this; }
        public Builder DE049_CURRENCY_CODE(String v)          { r.DE049_CURRENCY_CODE = v;          return this; }
        public Builder DE052_PIN_BLOCK(String v)              { r.DE052_PIN_BLOCK = v;              return this; }
        public Builder DE064_MAC(String v)                    { r.DE064_MAC = v;                    return this; }
        public Builder requestHex(String v)                   { r.requestHex = v;                   return this; }
        public Builder responseHex(String v)                  { r.responseHex = v;                  return this; }
        public Builder requestFields(Map<String, String> v)   { r.requestFields = v;                return this; }
        public Builder responseFields(Map<String, String> v)  { r.responseFields = v;               return this; }
        public McAuthResult build()                           { return r; }
    }
}
