package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipm_files")
public class IpmFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name",             length = 100) private String        fileName;
    @Column(name = "file_path_binary",      length = 500) private String        filePathBinary;
    @Column(name = "file_path_ascii",       length = 500) private String        filePathAscii;
    @Column(name = "file_date")                           private LocalDate     fileDate;
    @Column(name = "generation_date")                     private LocalDateTime generationDate;
    @Column(name = "status",                length = 20)  private String        status;
    @Column(name = "nb_transactions")                     private Integer       nbTransactions;
    @Column(name = "total_amount")                        private Long          totalAmount;
    @Column(name = "total_amount_currency", length = 3)   private String        totalAmountCurrency;
    @Column(name = "file_id",               length = 50)  private String        fileId;
    @Column(name = "processing_mode",       length = 10)  private String        processingMode;
    @Column(name = "created_at")                          private LocalDateTime createdAt;
    @Column(name = "created_by",            length = 50)  private String        createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    public IpmFile() {
        this.generationDate = LocalDateTime.now();
        this.createdAt      = LocalDateTime.now();
        this.status         = "GENERATED";
        this.processingMode = "TEST";
        this.nbTransactions = 0;
        this.totalAmount    = 0L;
    }

    // Getters
    public Long          getId()                  { return id; }
    public String        getFileName()            { return fileName; }
    public String        getFilePathBinary()      { return filePathBinary; }
    public String        getFilePathAscii()       { return filePathAscii; }
    public LocalDate     getFileDate()            { return fileDate; }
    public LocalDateTime getGenerationDate()      { return generationDate; }
    public String        getStatus()              { return status; }
    public Integer       getNbTransactions()      { return nbTransactions; }
    public Long          getTotalAmount()         { return totalAmount; }
    public String        getTotalAmountCurrency() { return totalAmountCurrency; }
    public String        getFileId()              { return fileId; }
    public String        getProcessingMode()      { return processingMode; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public String        getCreatedBy()           { return createdBy; }
    public Execution     getExecution()           { return execution; }

    // Setters
    public void setId(Long v)                       { this.id = v; }
    public void setFileName(String v)               { this.fileName = v; }
    public void setFilePathBinary(String v)         { this.filePathBinary = v; }
    public void setFilePathAscii(String v)          { this.filePathAscii = v; }
    public void setFileDate(LocalDate v)            { this.fileDate = v; }
    public void setGenerationDate(LocalDateTime v)  { this.generationDate = v; }
    public void setStatus(String v)                 { this.status = v; }
    public void setNbTransactions(Integer v)        { this.nbTransactions = v; }
    public void setTotalAmount(Long v)              { this.totalAmount = v; }
    public void setTotalAmountCurrency(String v)    { this.totalAmountCurrency = v; }
    public void setFileId(String v)                 { this.fileId = v; }
    public void setProcessingMode(String v)         { this.processingMode = v; }
    public void setCreatedAt(LocalDateTime v)       { this.createdAt = v; }
    public void setCreatedBy(String v)              { this.createdBy = v; }
    public void setExecution(Execution v)           { this.execution = v; }
}
