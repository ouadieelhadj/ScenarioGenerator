package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_dmas_kek")
public class McDmasKek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_group_id",   length = 20)  private String memberGroupId;
    @Column(name = "key_length")                        private Integer keyLength = 24;
    @Column(name = "kek_clear",          length = 48)  private String kekClear;
    @Column(name = "kek_under_acq_lmk",  length = 128) private String kekUnderAcqLmk;
    @Column(name = "kek_under_iss_lmk",  length = 128) private String kekUnderIssLmk;
    @Column(name = "kcv",                length = 6)   private String kcv;
    @Column(name = "status",             length = 10)  private String status = "ACTIVE";
    @Column(name = "description",        length = 255) private String description;
    @Column(name = "created_at")                        private LocalDateTime createdAt = LocalDateTime.now();

    public McDmasKek() {}

    public Long          getId()             { return id; }
    public String        getMemberGroupId()  { return memberGroupId; }
    public Integer       getKeyLength()      { return keyLength; }
    public String        getKekClear()       { return kekClear; }
    public String        getKekUnderAcqLmk() { return kekUnderAcqLmk; }
    public String        getKekUnderIssLmk() { return kekUnderIssLmk; }
    public String        getKcv()            { return kcv; }
    public String        getStatus()         { return status; }
    public String        getDescription()    { return description; }
    public LocalDateTime getCreatedAt()      { return createdAt; }

    public void setId(Long v)                  { this.id = v; }
    public void setMemberGroupId(String v)     { this.memberGroupId = v; }
    public void setKeyLength(Integer v)        { this.keyLength = v; }
    public void setKekClear(String v)          { this.kekClear = v; }
    public void setKekUnderAcqLmk(String v)    { this.kekUnderAcqLmk = v; }
    public void setKekUnderIssLmk(String v)    { this.kekUnderIssLmk = v; }
    public void setKcv(String v)               { this.kcv = v; }
    public void setStatus(String v)            { this.status = v; }
    public void setDescription(String v)       { this.description = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
}
