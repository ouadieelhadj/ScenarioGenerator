package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipm_processing_log")
public class IpmProcessingLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id",      length = 50)   private String  fileId;
    @Column(name = "file_name",    length = 100)  private String  fileName;
    @Column(name = "file_path",    length = 500)  private String  filePath;
    @Column(name = "role",         length = 10)   private String  role;        // ACQUIRER | ISSUER
    @Column(name = "direction",    length = 3)    private String  direction;   // OUT | IN
    @Column(name = "action",       length = 15)   private String  action;      // GENERATED | READ
    @Column(name = "execution_id")                private Long    executionId;
    @Column(name = "record_count")                private Integer recordCount;
    @Column(name = "checksum",     length = 64)   private String  checksum;
    @Column(name = "status",       length = 15)   private String  status;
    @Column(name = "processed_at")                private LocalDateTime processedAt = LocalDateTime.now();

    public IpmProcessingLog() {}

    public Long          getId()          { return id; }
    public String        getFileId()      { return fileId; }
    public String        getFileName()    { return fileName; }
    public String        getFilePath()    { return filePath; }
    public String        getRole()        { return role; }
    public String        getDirection()   { return direction; }
    public String        getAction()      { return action; }
    public Long          getExecutionId() { return executionId; }
    public Integer       getRecordCount() { return recordCount; }
    public String        getChecksum()    { return checksum; }
    public String        getStatus()      { return status; }
    public LocalDateTime getProcessedAt() { return processedAt; }

    public void setId(Long v)            { this.id = v; }
    public void setFileId(String v)      { this.fileId = v; }
    public void setFileName(String v)    { this.fileName = v; }
    public void setFilePath(String v)    { this.filePath = v; }
    public void setRole(String v)        { this.role = v; }
    public void setDirection(String v)   { this.direction = v; }
    public void setAction(String v)      { this.action = v; }
    public void setExecutionId(Long v)   { this.executionId = v; }
    public void setRecordCount(Integer v){ this.recordCount = v; }
    public void setChecksum(String v)    { this.checksum = v; }
    public void setStatus(String v)      { this.status = v; }
    public void setProcessedAt(LocalDateTime v) { this.processedAt = v; }
}
