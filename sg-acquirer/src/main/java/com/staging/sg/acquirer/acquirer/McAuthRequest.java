package com.staging.sg.acquirer.acquirer;

/**
 * Request for Mastercard 0100 Authorization.
 *
 * Mandatory :
 *   DE002_PAN    — Card number (generated if null)
 *   DE004_AMOUNT — Amount in cents
 *
 * Optional (defaults from acquiring.yml if not provided) :
 *   DE003_PROCESSING_CODE
 *   DE018_MCC
 *   DE022_POS_ENTRY_MODE
 *   DE025_POS_CONDITION_CODE
 *   DE032_ACQUIRING_BIN
 *   DE033_FORWARDING_BIN
 *   DE041_TERMINAL_ID
 *   DE042_MERCHANT_ID
 *   DE043_MERCHANT_NAME
 *   DE049_CURRENCY_CODE
 *   DE052_PIN
 *
 * Auto-generated (never provided) :
 *   DE007_TRANSMISSION_DATE_TIME
 *   DE011_STAN
 *   DE012_LOCAL_TIME
 *   DE013_LOCAL_DATE
 *   DE037_RRN
 *   DE052_PIN_BLOCK (encrypted under ZPK)
 *   DE064_MAC       (calculated under ZAK)
 */
public class McAuthRequest {

    // ── Mandatory ─────────────────────────────────────────────
    private String DE002_PAN;
    private long   DE004_AMOUNT;

    // ── Optional ──────────────────────────────────────────────
    private String DE003_PROCESSING_CODE;
    private String DE018_MCC;
    private String DE022_POS_ENTRY_MODE;
    private String DE025_POS_CONDITION_CODE;
    private String DE032_ACQUIRING_BIN;
    private String DE033_FORWARDING_BIN;
    private String DE041_TERMINAL_ID;
    private String DE042_MERCHANT_ID;
    private String DE043_MERCHANT_NAME;
    private String DE049_CURRENCY_CODE;
    private String DE052_PIN;

    // ── Seed for reproducibility ──────────────────────────────
    private Long seed;
    private Long executionId;

    public McAuthRequest() {}

    // Getters
    public String getDE002_PAN()                { return DE002_PAN; }
    public long   getDE004_AMOUNT()             { return DE004_AMOUNT; }
    public String getDE003_PROCESSING_CODE()    { return DE003_PROCESSING_CODE; }
    public String getDE018_MCC()                { return DE018_MCC; }
    public String getDE022_POS_ENTRY_MODE()     { return DE022_POS_ENTRY_MODE; }
    public String getDE025_POS_CONDITION_CODE() { return DE025_POS_CONDITION_CODE; }
    public String getDE032_ACQUIRING_BIN()      { return DE032_ACQUIRING_BIN; }
    public String getDE033_FORWARDING_BIN()     { return DE033_FORWARDING_BIN; }
    public String getDE041_TERMINAL_ID()        { return DE041_TERMINAL_ID; }
    public String getDE042_MERCHANT_ID()        { return DE042_MERCHANT_ID; }
    public String getDE043_MERCHANT_NAME()      { return DE043_MERCHANT_NAME; }
    public String getDE049_CURRENCY_CODE()      { return DE049_CURRENCY_CODE; }
    public String getDE052_PIN()                { return DE052_PIN; }
    public Long   getSeed()                     { return seed; }
    public Long   getExecutionId()               { return executionId; }

    // Setters
    public void setDE002_PAN(String v)                { this.DE002_PAN = v; }
    public void setDE004_AMOUNT(long v)               { this.DE004_AMOUNT = v; }
    public void setDE003_PROCESSING_CODE(String v)    { this.DE003_PROCESSING_CODE = v; }
    public void setDE018_MCC(String v)                { this.DE018_MCC = v; }
    public void setDE022_POS_ENTRY_MODE(String v)     { this.DE022_POS_ENTRY_MODE = v; }
    public void setDE025_POS_CONDITION_CODE(String v) { this.DE025_POS_CONDITION_CODE = v; }
    public void setDE032_ACQUIRING_BIN(String v)      { this.DE032_ACQUIRING_BIN = v; }
    public void setDE033_FORWARDING_BIN(String v)     { this.DE033_FORWARDING_BIN = v; }
    public void setDE041_TERMINAL_ID(String v)        { this.DE041_TERMINAL_ID = v; }
    public void setDE042_MERCHANT_ID(String v)        { this.DE042_MERCHANT_ID = v; }
    public void setDE043_MERCHANT_NAME(String v)      { this.DE043_MERCHANT_NAME = v; }
    public void setDE049_CURRENCY_CODE(String v)      { this.DE049_CURRENCY_CODE = v; }
    public void setDE052_PIN(String v)                { this.DE052_PIN = v; }
    public void setSeed(Long v)                       { this.seed = v; }
    public void setExecutionId(Long v)                { this.executionId = v; }
}
