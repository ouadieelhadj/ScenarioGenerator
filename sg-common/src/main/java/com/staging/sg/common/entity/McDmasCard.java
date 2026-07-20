package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_dmas_cards")
public class McDmasCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pan",      length = 19, unique = true) private String pan;
    @Column(name = "pin",      length = 12) private String pin;       // PIN attendu (clair, simulateur)
    @Column(name = "balance")                private Long balance = 0L; // centimes
    @Column(name = "currency", length = 3)  private String currency = "840";
    @Column(name = "expiry",   length = 4)  private String expiry;     // YYMM
    @Column(name = "status",   length = 10) private String status = "ACTIVE";
    @Column(name = "created_at")             private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")             private LocalDateTime updatedAt = LocalDateTime.now();

    public McDmasCard() {}

    public Long          getId()        { return id; }
    public String        getPan()       { return pan; }
    public String        getPin()       { return pin; }
    public Long          getBalance()   { return balance; }
    public String        getCurrency()  { return currency; }
    public String        getExpiry()    { return expiry; }
    public String        getStatus()    { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long v)                 { this.id = v; }
    public void setPan(String v)              { this.pan = v; }
    public void setPin(String v)              { this.pin = v; }
    public void setBalance(Long v)            { this.balance = v; }
    public void setCurrency(String v)         { this.currency = v; }
    public void setExpiry(String v)           { this.expiry = v; }
    public void setStatus(String v)           { this.status = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
