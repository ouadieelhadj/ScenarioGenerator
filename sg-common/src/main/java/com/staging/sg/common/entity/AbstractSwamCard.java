package com.staging.sg.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

/**
 * Structure commune aux cartes SWAM, sans table partagee entre le membre et
 * le switch.
 */
@MappedSuperclass
public abstract class AbstractSwamCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 19)
    private String pan;

    @Column(nullable = false, length = 12)
    private String pin;

    @Column(nullable = false)
    private Long balance = 0L;

    @Column(nullable = false, length = 3)
    private String currency = "504";

    @Column(length = 4)
    private String expiry;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getPan() { return pan; }
    public void setPan(String value) { this.pan = value; }
    public String getPin() { return pin; }
    public void setPin(String value) { this.pin = value; }
    public Long getBalance() { return balance; }
    public void setBalance(Long value) { this.balance = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String value) { this.expiry = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
