package com.staging.sg.swam.lis.common.persistence;

import com.staging.sg.swam.lis.common.model.BusinessDayStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AbstractBusinessDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_member_id", nullable = false, length = 20)
    private String bankMemberId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BusinessDayStatus status = BusinessDayStatus.OPEN;

    @Column(name = "cutoff_at")
    private LocalDateTime cutoffAt;
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private long version;

    public Long getId() { return id; }
    public String getBankMemberId() { return bankMemberId; }
    public void setBankMemberId(String value) { bankMemberId = value; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate value) { businessDate = value; }
    public BusinessDayStatus getStatus() { return status; }
    public void setStatus(BusinessDayStatus value) { status = value; }
    public LocalDateTime getCutoffAt() { return cutoffAt; }
    public void setCutoffAt(LocalDateTime value) { cutoffAt = value; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime value) { closedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    public long getVersion() { return version; }
}
