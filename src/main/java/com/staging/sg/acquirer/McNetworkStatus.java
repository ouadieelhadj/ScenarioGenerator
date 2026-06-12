package com.staging.sg.acquirer;

public class McNetworkStatus {

    private boolean keysExchanged;
    private boolean signedOn;
    private String  signOnTime;
    private String  lastEchoTime;
    private String  zmkKcv;
    private String  zpkKcv;
    private String  zakKcv;
    private int     echoIntervalSeconds;
    private String  mode;
    private String  host;
    private int     port;

    public McNetworkStatus() {}

    public boolean isKeysExchanged()       { return keysExchanged; }
    public boolean isSignedOn()            { return signedOn; }
    public String  getSignOnTime()         { return signOnTime; }
    public String  getLastEchoTime()       { return lastEchoTime; }
    public String  getZmkKcv()            { return zmkKcv; }
    public String  getZpkKcv()            { return zpkKcv; }
    public String  getZakKcv()            { return zakKcv; }
    public int     getEchoIntervalSeconds(){ return echoIntervalSeconds; }
    public String  getMode()              { return mode; }
    public String  getHost()              { return host; }
    public int     getPort()              { return port; }

    public void setKeysExchanged(boolean v)        { this.keysExchanged = v; }
    public void setSignedOn(boolean v)             { this.signedOn = v; }
    public void setSignOnTime(String v)            { this.signOnTime = v; }
    public void setLastEchoTime(String v)          { this.lastEchoTime = v; }
    public void setZmkKcv(String v)               { this.zmkKcv = v; }
    public void setZpkKcv(String v)               { this.zpkKcv = v; }
    public void setZakKcv(String v)               { this.zakKcv = v; }
    public void setEchoIntervalSeconds(int v)      { this.echoIntervalSeconds = v; }
    public void setMode(String v)                  { this.mode = v; }
    public void setHost(String v)                  { this.host = v; }
    public void setPort(int v)                     { this.port = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final McNetworkStatus s = new McNetworkStatus();
        public Builder keysExchanged(boolean v)        { s.keysExchanged = v;        return this; }
        public Builder signedOn(boolean v)             { s.signedOn = v;             return this; }
        public Builder signOnTime(String v)            { s.signOnTime = v;           return this; }
        public Builder lastEchoTime(String v)          { s.lastEchoTime = v;         return this; }
        public Builder zmkKcv(String v)               { s.zmkKcv = v;              return this; }
        public Builder zpkKcv(String v)               { s.zpkKcv = v;              return this; }
        public Builder zakKcv(String v)               { s.zakKcv = v;              return this; }
        public Builder echoIntervalSeconds(int v)      { s.echoIntervalSeconds = v;  return this; }
        public Builder mode(String v)                  { s.mode = v;                return this; }
        public Builder host(String v)                  { s.host = v;                return this; }
        public Builder port(int v)                     { s.port = v;                return this; }
        public McNetworkStatus build()                 { return s; }
    }
}
