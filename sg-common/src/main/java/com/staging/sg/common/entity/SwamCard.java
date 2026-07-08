package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Carte de la base emetteur SWAM (table swam_cards). */
@Entity
@Table(name = "swam_cards")
public class SwamCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 19) private String pan;
    @Column(nullable = false, length = 12)                private String pin;
    @Column(nullable = false)                             private Long balance = 0L;
    @Column(nullable = false, length = 3)                 private String currency = "504";
    @Column(length = 4)                                   private String expiry;
    @Column(nullable = false, length = 10)                private String status = "ACTIVE";
    @Column(name = "created_at")                          private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                          private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getPan() { return pan; }
    public void setPan(String v) { this.pan = v; }
    public String getPin() { return pin; }
    public void setPin(String v) { this.pin = v; }
    public Long getBalance() { return balance; }
    public void setBalance(Long v) { this.balance = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String v) { this.expiry = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
