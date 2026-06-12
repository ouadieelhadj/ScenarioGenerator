package com.staging.sg.hsm;

public class HsmKeyResult {

    public enum KeyType { ZMK, ZPK, ZAK }

    private KeyType keyType;
    private byte[]  keyValue;
    private byte[]  keyEncryptedUnderKek;
    private byte[]  keyEncryptedUnderZmk;
    private String  keyCheckValue;
    private boolean success;
    private String  errorMessage;

    public HsmKeyResult() {}

    public KeyType getKeyType()              { return keyType; }
    public byte[]  getKeyValue()             { return keyValue; }
    public byte[]  getKeyEncryptedUnderKek() { return keyEncryptedUnderKek; }
    public byte[]  getKeyEncryptedUnderZmk() { return keyEncryptedUnderZmk; }
    public String  getKeyCheckValue()        { return keyCheckValue; }
    public boolean isSuccess()               { return success; }
    public String  getErrorMessage()         { return errorMessage; }

    public void setKeyType(KeyType v)              { this.keyType = v; }
    public void setKeyValue(byte[] v)              { this.keyValue = v; }
    public void setKeyEncryptedUnderKek(byte[] v)  { this.keyEncryptedUnderKek = v; }
    public void setKeyEncryptedUnderZmk(byte[] v)  { this.keyEncryptedUnderZmk = v; }
    public void setKeyCheckValue(String v)         { this.keyCheckValue = v; }
    public void setSuccess(boolean v)              { this.success = v; }
    public void setErrorMessage(String v)          { this.errorMessage = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private final HsmKeyResult r = new HsmKeyResult();
        public Builder keyType(KeyType v)              { r.keyType = v;               return this; }
        public Builder keyValue(byte[] v)              { r.keyValue = v;              return this; }
        public Builder keyEncryptedUnderKek(byte[] v)  { r.keyEncryptedUnderKek = v;  return this; }
        public Builder keyEncryptedUnderZmk(byte[] v)  { r.keyEncryptedUnderZmk = v;  return this; }
        public Builder keyCheckValue(String v)         { r.keyCheckValue = v;         return this; }
        public Builder success(boolean v)              { r.success = v;               return this; }
        public Builder errorMessage(String v)          { r.errorMessage = v;          return this; }
        public HsmKeyResult build()                    { return r; }
    }
}
