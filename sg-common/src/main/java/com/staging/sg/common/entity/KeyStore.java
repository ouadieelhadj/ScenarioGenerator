package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "key_store")
public class KeyStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_group_id", length = 20)  private String memberGroupId;
    @Column(name = "key_type",        length = 3)   private String keyType;
    @Column(name = "key_length")                     private Integer keyLength = 24;
    @Column(name = "encrypted_value", length = 64)   private String encryptedValue;
    @Column(name = "kcv",             length = 6)    private String kcv;
    @Column(name = "status",          length = 10)   private String status = "ACTIVE";
    @Column(name = "description",     length = 255)  private String description;
    @Column(name = "created_at")                      private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "activated_at")                    private LocalDateTime activatedAt;

    public KeyStore() {}

    public Long          getId()             { return id; }
    public String        getMemberGroupId()  { return memberGroupId; }
    public String        getKeyType()        { return keyType; }
    public Integer       getKeyLength()      { return keyLength; }
    public String        getEncryptedValue() { return encryptedValue; }
    public String        getKcv()            { return kcv; }
    public String        getStatus()         { return status; }
    public String        getDescription()    { return description; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public LocalDateTime getActivatedAt()    { return activatedAt; }

    public void setId(Long v)                  { this.id = v; }
    public void setMemberGroupId(String v)     { this.memberGroupId = v; }
    public void setKeyType(String v)           { this.keyType = v; }
    public void setKeyLength(Integer v)        { this.keyLength = v; }
    public void setEncryptedValue(String v)    { this.encryptedValue = v; }
    public void setKcv(String v)               { this.kcv = v; }
    public void setStatus(String v)            { this.status = v; }
    public void setDescription(String v)       { this.description = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public void setActivatedAt(LocalDateTime v){ this.activatedAt = v; }
}
