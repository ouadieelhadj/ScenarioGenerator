package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_types")
public class MessageType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 255)
    private String description;

    @Column(name = "processing_codes", columnDefinition = "TEXT")
    private String processingCodes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MessageType() {}

    // Getters
    public Long          getId()              { return id; }
    public String        getCode()            { return code; }
    public String        getName()            { return name; }
    public String        getCategory()        { return category; }
    public String        getDescription()     { return description; }
    public String        getProcessingCodes() { return processingCodes; }
    public boolean       isActive()           { return active; }
    public LocalDateTime getCreatedAt()       { return createdAt; }

    // Setters
    public void setId(Long v)                  { this.id = v; }
    public void setCode(String v)              { this.code = v; }
    public void setName(String v)              { this.name = v; }
    public void setCategory(String v)          { this.category = v; }
    public void setDescription(String v)       { this.description = v; }
    public void setProcessingCodes(String v)   { this.processingCodes = v; }
    public void setActive(boolean v)           { this.active = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
}
