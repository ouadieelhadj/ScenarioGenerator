package com.staging.sg.mc.sms.acquirer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_sms_acq_keys")
public class McSmsAcqKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_group_id", nullable = false) private String memberGroupId;
    @Column(name = "key_type", nullable = false)        private String keyType;  // PEK, MAK
    @Column(name = "key_length", nullable = false)      private Integer keyLength = 24;
    @Column(name = "key_under_lmk")                    private String keyUnderLmk;
    @Column(name = "key_under_kek")                    private String keyUnderKek;
    @Column(name = "kcv")                              private String kcv;
    @Column(name = "status", nullable = false)          private String status = "ACTIVE";
    @Column(name = "created_at")                        private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
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
}
