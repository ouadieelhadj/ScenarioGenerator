package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "generated_transactions")
public class GeneratedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false) private Long campaignId;
    @Column(name = "tx_type", length = 30)                  private String txType;
    @Column(name = "de2_pan", length = 19)                  private String de2Pan;
    @Column(name = "de3_processing_code", length = 6)       private String de3ProcessingCode;
    @Column(name = "de4_amount")                            private Long de4Amount;
    @Column(name = "de7_transmission_dt", length = 10)      private String de7TransmissionDt;
    @Column(name = "de11_stan", length = 6)                 private String de11Stan;
    @Column(name = "de12_local_time", length = 6)           private String de12LocalTime;
    @Column(name = "de13_local_date", length = 4)           private String de13LocalDate;
    @Column(name = "de14_expiry", length = 4)               private String de14Expiry;
    @Column(name = "de18_mcc", length = 4)                  private String de18Mcc;
    @Column(name = "de22_pos_entry_mode", length = 3)       private String de22PosEntryMode;
    @Column(name = "de25_pos_condition", length = 2)        private String de25PosCondition;
    @Column(name = "de32_acquirer_id", length = 11)         private String de32AcquirerId;
    @Column(name = "de37_rrn", length = 12)                 private String de37Rrn;
    @Column(name = "de41_terminal_id", length = 8)          private String de41TerminalId;
    @Column(name = "de42_merchant_id", length = 15)         private String de42MerchantId;
    @Column(name = "de43_merchant_name_loc", length = 40)   private String de43MerchantNameLoc;
    @Column(name = "de49_currency", length = 3)             private String de49Currency;
    @Column(name = "created_at")                            private LocalDateTime createdAt = LocalDateTime.now();

    public GeneratedTransaction() {}

    public Long getId()                 { return id; }
    public Long getCampaignId()         { return campaignId; }
    public String getTxType()           { return txType; }
    public String getDe2Pan()           { return de2Pan; }
    public String getDe3ProcessingCode(){ return de3ProcessingCode; }
    public Long getDe4Amount()          { return de4Amount; }
    public String getDe7TransmissionDt(){ return de7TransmissionDt; }
    public String getDe11Stan()         { return de11Stan; }
    public String getDe12LocalTime()    { return de12LocalTime; }
    public String getDe13LocalDate()    { return de13LocalDate; }
    public String getDe14Expiry()       { return de14Expiry; }
    public String getDe18Mcc()          { return de18Mcc; }
    public String getDe22PosEntryMode() { return de22PosEntryMode; }
    public String getDe25PosCondition() { return de25PosCondition; }
    public String getDe32AcquirerId()   { return de32AcquirerId; }
    public String getDe37Rrn()          { return de37Rrn; }
    public String getDe41TerminalId()   { return de41TerminalId; }
    public String getDe42MerchantId()   { return de42MerchantId; }
    public String getDe43MerchantNameLoc(){ return de43MerchantNameLoc; }
    public String getDe49Currency()     { return de49Currency; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long v)                 { this.id = v; }
    public void setCampaignId(Long v)         { this.campaignId = v; }
    public void setTxType(String v)           { this.txType = v; }
    public void setDe2Pan(String v)           { this.de2Pan = v; }
    public void setDe3ProcessingCode(String v){ this.de3ProcessingCode = v; }
    public void setDe4Amount(Long v)          { this.de4Amount = v; }
    public void setDe7TransmissionDt(String v){ this.de7TransmissionDt = v; }
    public void setDe11Stan(String v)         { this.de11Stan = v; }
    public void setDe12LocalTime(String v)    { this.de12LocalTime = v; }
    public void setDe13LocalDate(String v)    { this.de13LocalDate = v; }
    public void setDe14Expiry(String v)       { this.de14Expiry = v; }
    public void setDe18Mcc(String v)          { this.de18Mcc = v; }
    public void setDe22PosEntryMode(String v) { this.de22PosEntryMode = v; }
    public void setDe25PosCondition(String v) { this.de25PosCondition = v; }
    public void setDe32AcquirerId(String v)   { this.de32AcquirerId = v; }
    public void setDe37Rrn(String v)          { this.de37Rrn = v; }
    public void setDe41TerminalId(String v)   { this.de41TerminalId = v; }
    public void setDe42MerchantId(String v)   { this.de42MerchantId = v; }
    public void setDe43MerchantNameLoc(String v){ this.de43MerchantNameLoc = v; }
    public void setDe49Currency(String v)     { this.de49Currency = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
