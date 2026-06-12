package com.staging.sg.acquirer;

public class McNetworkResult {

    private String  type;
    private String  stan;
    private String  responseCode;
    private boolean success;
    private String  message;
    private String  requestHex;
    private String  responseHex;

    public McNetworkResult() {}

    public String  getType()         { return type; }
    public String  getStan()         { return stan; }
    public String  getResponseCode() { return responseCode; }
    public boolean isSuccess()       { return success; }
    public String  getMessage()      { return message; }
    public String  getRequestHex()   { return requestHex; }
    public String  getResponseHex()  { return responseHex; }

    public void setType(String v)         { this.type = v; }
    public void setStan(String v)         { this.stan = v; }
    public void setResponseCode(String v) { this.responseCode = v; }
    public void setSuccess(boolean v)     { this.success = v; }
    public void setMessage(String v)      { this.message = v; }
    public void setRequestHex(String v)   { this.requestHex = v; }
    public void setResponseHex(String v)  { this.responseHex = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final McNetworkResult r = new McNetworkResult();
        public Builder type(String v)         { r.type = v;         return this; }
        public Builder stan(String v)         { r.stan = v;         return this; }
        public Builder responseCode(String v) { r.responseCode = v; return this; }
        public Builder success(boolean v)     { r.success = v;      return this; }
        public Builder message(String v)      { r.message = v;      return this; }
        public Builder requestHex(String v)   { r.requestHex = v;   return this; }
        public Builder responseHex(String v)  { r.responseHex = v;  return this; }
        public McNetworkResult build()        { return r; }
    }
}
