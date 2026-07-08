package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Cle acquereur SWAM (table swam_acq_keys). */
@Entity
@Table(name = "swam_acq_keys")
public class SwamAcqKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_group_id", nullable = false, length = 20) private String memberGroupId;
    @Column(name = "key_type", nullable = false, length = 3)         private String keyType;
    @Column(name = "key_length", nullable = false)                  private Integer keyLength = 24;
    @Column(name = "key_under_lmk", length = 64)                    private String keyUnderLmk;
    @Column(name = "key_under_kek", length = 64)                    private String keyUnderKek;
    @Column(length = 6)                                             private String kcv;
    @Column(nullable = false, length = 10)                          private String status = "ACTIVE";
    @Column(name = "created_at")                                    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getMemberGroupId() { return memberGroupId; }
    public void setMemberGroupId(String v) { this.memberGroupId = v; }
    public String getKeyType() { return keyType; }
    public void setKeyType(String v) { this.keyType = v; }
    public Integer getKeyLength() { return keyLength; }
    public void setKeyLength(Integer v) { this.keyLength = v; }
    public String getKeyUnderLmk() { return keyUnderLmk; }
    public void setKeyUnderLmk(String v) { this.keyUnderLmk = v; }
    public String getKeyUnderKek() { return keyUnderKek; }
    public void setKeyUnderKek(String v) { this.keyUnderKek = v; }
    public String getKcv() { return kcv; }
    public void setKcv(String v) { this.kcv = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
