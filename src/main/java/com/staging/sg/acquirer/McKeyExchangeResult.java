package com.staging.sg.acquirer;

public class McKeyExchangeResult {

    private boolean success;
    private String  message;
    private String  zmkKcv;
    private String  zpkKcv;
    private String  zakKcv;
    private String  requestHex;
    private String  responseHex;

    public McKeyExchangeResult() {}

    public boolean isSuccess()       { return success; }
    public String  getMessage()      { return message; }
    public String  getZmkKcv()      { return zmkKcv; }
    public String  getZpkKcv()      { return zpkKcv; }
    public String  getZakKcv()      { return zakKcv; }
    public String  getRequestHex()  { return requestHex; }
    public String  getResponseHex() { return responseHex; }

    public void setSuccess(boolean v)     { this.success = v; }
    public void setMessage(String v)      { this.message = v; }
    public void setZmkKcv(String v)       { this.zmkKcv = v; }
    public void setZpkKcv(String v)       { this.zpkKcv = v; }
    public void setZakKcv(String v)       { this.zakKcv = v; }
    public void setRequestHex(String v)   { this.requestHex = v; }
    public void setResponseHex(String v)  { this.responseHex = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final McKeyExchangeResult r = new McKeyExchangeResult();
        public Builder success(boolean v)     { r.success = v;     return this; }
        public Builder message(String v)      { r.message = v;     return this; }
        public Builder zmkKcv(String v)       { r.zmkKcv = v;      return this; }
        public Builder zpkKcv(String v)       { r.zpkKcv = v;      return this; }
        public Builder zakKcv(String v)       { r.zakKcv = v;      return this; }
        public Builder requestHex(String v)   { r.requestHex = v;  return this; }
        public Builder responseHex(String v)  { r.responseHex = v; return this; }
        public McKeyExchangeResult build()    { return r; }
    }
}
