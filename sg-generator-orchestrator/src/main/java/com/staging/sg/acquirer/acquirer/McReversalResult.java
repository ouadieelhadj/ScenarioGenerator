package com.staging.sg.acquirer.acquirer;

public class McReversalResult {

    private String  DE002_PAN;
    private String  DE039_RESPONSE_CODE;
    private String  DE038_AUTH_CODE;
    private String  DE037_RETRIEVAL_REF;
    private boolean reversed;
    private String  requestHex;
    private String  responseHex;
    private String  error;

    public McReversalResult() {}

    // Getters
    public String  getDE002_PAN()           { return DE002_PAN; }
    public String  getDE039_RESPONSE_CODE() { return DE039_RESPONSE_CODE; }
    public String  getDE038_AUTH_CODE()     { return DE038_AUTH_CODE; }
    public String  getDE037_RETRIEVAL_REF() { return DE037_RETRIEVAL_REF; }
    public boolean isReversed()             { return reversed; }
    public String  getRequestHex()          { return requestHex; }
    public String  getResponseHex()         { return responseHex; }
    public String  getError()               { return error; }

    // Setters
    public void setDE002_PAN(String v)           { this.DE002_PAN = v; }
    public void setDE039_RESPONSE_CODE(String v) { this.DE039_RESPONSE_CODE = v; }
    public void setDE038_AUTH_CODE(String v)     { this.DE038_AUTH_CODE = v; }
    public void setDE037_RETRIEVAL_REF(String v) { this.DE037_RETRIEVAL_REF = v; }
    public void setReversed(boolean v)           { this.reversed = v; }
    public void setRequestHex(String v)          { this.requestHex = v; }
    public void setResponseHex(String v)         { this.responseHex = v; }
    public void setError(String v)               { this.error = v; }
}
