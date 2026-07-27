package com.staging.sg.swam.lis.common.persistence;

import com.staging.sg.swam.lis.common.model.BatchStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
public abstract class AbstractBatchExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_day_id", nullable = false)
    private Long businessDayId;
    @Column(name = "batch_type", nullable = false, length = 32)
    private String batchType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status = BatchStatus.PENDING;
    @Column(name = "correlation_id", nullable = false, unique = true)
    private UUID correlationId = UUID.randomUUID();
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "read_count", nullable = false)
    private long readCount;
    @Column(name = "write_count", nullable = false)
    private long writeCount;
    @Column(name = "skip_count", nullable = false)
    private long skipCount;
    @Column(name = "error_count", nullable = false)
    private long errorCount;
    @Column(name = "requested_by", length = 80)
    private String requestedBy;
    @Column(name = "error_summary", length = 1000)
    private String errorSummary;
    @Version
    private long version;

    public Long getId() { return id; }
    public Long getBusinessDayId() { return businessDayId; }
    public void setBusinessDayId(Long value) { businessDayId = value; }
    public String getBatchType() { return batchType; }
    public void setBatchType(String value) { batchType = value; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus value) { status = value; }
    public UUID getCorrelationId() { return correlationId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public long getReadCount() { return readCount; }
    public void setReadCount(long value) { readCount = value; }
    public long getWriteCount() { return writeCount; }
    public void setWriteCount(long value) { writeCount = value; }
    public long getSkipCount() { return skipCount; }
    public void setSkipCount(long value) { skipCount = value; }
    public long getErrorCount() { return errorCount; }
    public void setErrorCount(long value) { errorCount = value; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String value) { requestedBy = value; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String value) { errorSummary = value; }
    public long getVersion() { return version; }
}
