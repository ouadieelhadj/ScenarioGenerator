package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_dmas_member_keys")
public class McDmasMemberKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_group_id", length = 20) private String memberGroupId;
    @Column(name = "key_type",        length = 3)  private String keyType;
    @Column(name = "key_length")                    private Integer keyLength = 24;
    @Column(name = "key_under_lmk",   length = 64) private String keyUnderLmk;
    @Column(name = "key_under_kek",   length = 64) private String keyUnderKek;
    @Column(name = "kcv",             length = 6)  private String kcv;
    @Column(name = "status",          length = 10) private String status = "ACTIVE";
    @Column(name = "created_at")                    private LocalDateTime createdAt = LocalDateTime.now();

    public McDmasMemberKey() {}

    public Long          getId()            { return id; }
    public String        getMemberGroupId() { return memberGroupId; }
    public String        getKeyType()       { return keyType; }
    public Integer       getKeyLength()     { return keyLength; }
    public String        getKeyUnderLmk()   { return keyUnderLmk; }
    public String        getKeyUnderKek()   { return keyUnderKek; }
    public String        getKcv()           { return kcv; }
    public String        getStatus()        { return status; }
    public LocalDateTime getCreatedAt()     { return createdAt; }

    public void setId(Long v)                 { this.id = v; }
    public void setMemberGroupId(String v)    { this.memberGroupId = v; }
    public void setKeyType(String v)          { this.keyType = v; }
    public void setKeyLength(Integer v)       { this.keyLength = v; }
    public void setKeyUnderLmk(String v)      { this.keyUnderLmk = v; }
    public void setKeyUnderKek(String v)      { this.keyUnderKek = v; }
    public void setKcv(String v)              { this.kcv = v; }
    public void setStatus(String v)           { this.status = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
