package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100) private String name;
    @Column(length = 255)                    private String description;
    @Column(length = 20, nullable = false)   private String network  = "MASTERCARD";
    @Column(length = 20, nullable = false)   private String channel  = "POS";
    @Column(length = 3,  nullable = false)   private String country  = "FR";
    @Column(length = 3,  nullable = false)   private String currency = "EUR";
    @Column(name = "amount_min", nullable = false) private Long amountMin = 1000L;
    @Column(name = "amount_max", nullable = false) private Long amountMax = 50000L;
    @Column(length = 4)                      private String mcc = "5999";
    @Column(name = "tx_count", nullable = false)   private Integer txCount = 100;
    @Column(name = "tx_type", length = 30, nullable = false) private String txType = "purchase";
    @Column(length = 20, nullable = false)   private String status = "DRAFT";
    @Column(name = "created_at")             private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "selected_fields", columnDefinition = "TEXT") private String selectedFields;
    @Column(name = "bin_range_id") private Long binRangeId;

    public Campaign() {}

    public Long getId()              { return id; }
    public String getName()          { return name; }
    public String getDescription()   { return description; }
    public String getNetwork()       { return network; }
    public String getChannel()       { return channel; }
    public String getCountry()       { return country; }
    public String getCurrency()      { return currency; }
    public Long getAmountMin()       { return amountMin; }
    public Long getAmountMax()       { return amountMax; }
    public String getMcc()           { return mcc; }
    public Integer getTxCount()      { return txCount; }
    public String getTxType()        { return txType; }
    public String getStatus()        { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getSelectedFields()   { return selectedFields; }
    public Long getBinRangeId()         { return binRangeId; }

    public void setId(Long v)              { this.id = v; }
    public void setName(String v)          { this.name = v; }
    public void setDescription(String v)   { this.description = v; }
    public void setNetwork(String v)       { this.network = v; }
    public void setChannel(String v)       { this.channel = v; }
    public void setCountry(String v)       { this.country = v; }
    public void setCurrency(String v)      { this.currency = v; }
    public void setAmountMin(Long v)       { this.amountMin = v; }
    public void setAmountMax(Long v)       { this.amountMax = v; }
    public void setMcc(String v)           { this.mcc = v; }
    public void setTxCount(Integer v)      { this.txCount = v; }
    public void setTxType(String v)        { this.txType = v; }
    public void setStatus(String v)        { this.status = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public void setSelectedFields(String v)   { this.selectedFields = v; }
    public void setBinRangeId(Long v)         { this.binRangeId = v; }
}
