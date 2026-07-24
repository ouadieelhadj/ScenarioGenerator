package com.staging.sg.mc.sms.issuer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_sms_cards")
public class McSmsCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pan",          nullable = false, unique = true) private String pan;
    @Column(name = "pin",          nullable = false)                private String pin;
    @Column(name = "balance",      nullable = false)                private Long balance = 0L;
    @Column(name = "currency",     nullable = false)                private String currency = "504";
    @Column(name = "expiry")                                        private String expiry;
    @Column(name = "cvv2")                                          private String cvv2;
    @Column(name = "service_code")                                  private String serviceCode = "101";
    @Column(name = "status",       nullable = false)                private String status = "ACTIVE";
    @Column(name = "created_at")                                    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                                    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
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
    public String getCvv2() { return cvv2; }
    public void setCvv2(String v) { this.cvv2 = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
