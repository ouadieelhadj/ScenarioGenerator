package com.staging.sg.swam.lis.common.persistence;

import com.staging.sg.swam.lis.common.model.LisDirection;
import com.staging.sg.swam.lis.common.model.LisFileStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AbstractLisFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_day_id", nullable = false)
    private Long businessDayId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LisDirection direction;
    @Column(name = "file_name", nullable = false, length = 160)
    private String fileName;
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;
    @Column(name = "source_member", nullable = false, length = 20)
    private String sourceMember;
    @Column(name = "destination_member", nullable = false, length = 20)
    private String destinationMember;
    @Column(name = "processing_date", nullable = false)
    private LocalDate processingDate;
    @Column(name = "file_sequence", nullable = false)
    private int fileSequence;
    @Column(name = "regeneration_status", nullable = false, length = 1)
    private String regenerationStatus = "N";
    @Column(name = "lis_version", nullable = false, length = 10)
    private String lisVersion = "4.13";
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    @Column(name = "physical_record_count", nullable = false)
    private int physicalRecordCount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LisFileStatus status = LisFileStatus.CREATED;
    @Column(name = "original_file_id")
    private Long originalFileId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Version
    private long version;

    public Long getId() { return id; }
    public Long getBusinessDayId() { return businessDayId; }
    public void setBusinessDayId(Long value) { businessDayId = value; }
    public LisDirection getDirection() { return direction; }
    public void setDirection(LisDirection value) { direction = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { fileName = value; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String value) { storagePath = value; }
    public String getSourceMember() { return sourceMember; }
    public void setSourceMember(String value) { sourceMember = value; }
    public String getDestinationMember() { return destinationMember; }
    public void setDestinationMember(String value) { destinationMember = value; }
    public LocalDate getProcessingDate() { return processingDate; }
    public void setProcessingDate(LocalDate value) { processingDate = value; }
    public int getFileSequence() { return fileSequence; }
    public void setFileSequence(int value) { fileSequence = value; }
    public String getRegenerationStatus() { return regenerationStatus; }
    public void setRegenerationStatus(String value) { regenerationStatus = value; }
    public String getLisVersion() { return lisVersion; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long value) { byteSize = value; }
    public int getPhysicalRecordCount() { return physicalRecordCount; }
    public void setPhysicalRecordCount(int value) { physicalRecordCount = value; }
    public LisFileStatus getStatus() { return status; }
    public void setStatus(LisFileStatus value) { status = value; }
    public Long getOriginalFileId() { return originalFileId; }
    public void setOriginalFileId(Long value) { originalFileId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
