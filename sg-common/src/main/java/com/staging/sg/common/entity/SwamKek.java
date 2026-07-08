package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** KEK SWAM (table swam_kek, partagee). */
@Entity
@Table(name = "swam_kek")
public class SwamKek {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_group_id", nullable = false, length = 20) private String memberGroupId;
    @Column(name = "key_length", nullable = false)                  private Integer keyLength = 24;
    @Column(name = "kek_clear", length = 48)                        private String kekClear;
    @Column(name = "kek_under_acq_lmk", length = 128)              private String kekUnderAcqLmk;
    @Column(name = "kek_under_iss_lmk", length = 128)              private String kekUnderIssLmk;
    @Column(length = 6)                                            private String kcv;
    @Column(nullable = false, length = 10)                         private String status = "ACTIVE";
    @Column(length = 255)                                         private String description;
    @Column(name = "created_at")                                  private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getMemberGroupId() { return memberGroupId; }
    public void setMemberGroupId(String v) { this.memberGroupId = v; }
    public Integer getKeyLength() { return keyLength; }
    public void setKeyLength(Integer v) { this.keyLength = v; }
    public String getKekClear() { return kekClear; }
    public void setKekClear(String v) { this.kekClear = v; }
    public String getKekUnderAcqLmk() { return kekUnderAcqLmk; }
    public void setKekUnderAcqLmk(String v) { this.kekUnderAcqLmk = v; }
    public String getKekUnderIssLmk() { return kekUnderIssLmk; }
    public void setKekUnderIssLmk(String v) { this.kekUnderIssLmk = v; }
    public String getKcv() { return kcv; }
    public void setKcv(String v) { this.kcv = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
