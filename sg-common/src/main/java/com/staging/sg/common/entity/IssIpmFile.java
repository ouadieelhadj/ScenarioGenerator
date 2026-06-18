package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "iss_ipm_files")
public class IssIpmFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name",        length = 100, nullable = false) private String  fileName;
    @Column(name = "file_path_binary", length = 500)                   private String  filePathBinary;
    @Column(name = "file_path_ascii",  length = 500)                   private String  filePathAscii;
    @Column(name = "file_date",        nullable = false)               private LocalDate fileDate;
    @Column(name = "generation_date")                                  private LocalDateTime generationDate = LocalDateTime.now();
    @Column(name = "status",           length = 20)                    private String  status = "GENERATED";
    @Column(name = "direction",        length = 3)                     private String  direction = "OUT";
    @Column(name = "nb_transactions")                                  private Integer nbTransactions = 0;
    @Column(name = "total_amount")                                     private Long    totalAmount = 0L;
    @Column(name = "total_amount_currency", length = 3)                private String  totalAmountCurrency;
    @Column(name = "file_id",          length = 50)                    private String  fileId;
    @Column(name = "processing_mode",  length = 10)                    private String  processingMode = "TEST";
    @Column(name = "execution_id")                                     private Long    executionId;
    @Column(name = "created_at")                                       private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by",       length = 50)                    private String  createdBy;

    public IssIpmFile() {}

    public Long          getId()                  { return id; }
    public String        getFileName()            { return fileName; }
    public String        getFilePathBinary()      { return filePathBinary; }
    public String        getFilePathAscii()       { return filePathAscii; }
    public LocalDate     getFileDate()            { return fileDate; }
    public LocalDateTime getGenerationDate()      { return generationDate; }
    public String        getStatus()              { return status; }
    public String        getDirection()           { return direction; }
    public Integer       getNbTransactions()      { return nbTransactions; }
    public Long          getTotalAmount()         { return totalAmount; }
    public String        getTotalAmountCurrency() { return totalAmountCurrency; }
    public String        getFileId()              { return fileId; }
    public String        getProcessingMode()      { return processingMode; }
    public Long          getExecutionId()         { return executionId; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public String        getCreatedBy()           { return createdBy; }

    public void setId(Long v)                  { this.id = v; }
    public void setFileName(String v)          { this.fileName = v; }
    public void setFilePathBinary(String v)    { this.filePathBinary = v; }
    public void setFilePathAscii(String v)     { this.filePathAscii = v; }
    public void setFileDate(LocalDate v)       { this.fileDate = v; }
    public void setGenerationDate(LocalDateTime v) { this.generationDate = v; }
    public void setStatus(String v)            { this.status = v; }
    public void setDirection(String v)         { this.direction = v; }
    public void setNbTransactions(Integer v)   { this.nbTransactions = v; }
    public void setTotalAmount(Long v)         { this.totalAmount = v; }
    public void setTotalAmountCurrency(String v){ this.totalAmountCurrency = v; }
    public void setFileId(String v)            { this.fileId = v; }
    public void setProcessingMode(String v)    { this.processingMode = v; }
    public void setExecutionId(Long v)         { this.executionId = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public void setCreatedBy(String v)         { this.createdBy = v; }
}
