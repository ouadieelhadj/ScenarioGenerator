package com.staging.sg.mc.sms.acquirer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mc_sms_kek")
public class McSmsKek {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_group_id", nullable = false) private String memberGroupId;
    @Column(name = "key_length", nullable = false)      private Integer keyLength = 24;
    @Column(name = "kek_clear")                         private String kekClear;
    @Column(name = "kek_under_acq_lmk")                private String kekUnderAcqLmk;
    @Column(name = "kcv")                               private String kcv;
    @Column(name = "status", nullable = false)          private String status = "ACTIVE";
    @Column(name = "description")                       private String description;
    @Column(name = "created_at")                        private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getMemberGroupId() { return memberGroupId; }
    public void setMemberGroupId(String v) { this.memberGroupId = v; }
    public Integer getKeyLength() { return keyLength; }
    public void setKeyLength(Integer v) { this.keyLength = v; }
    public String getKekClear() { return kekClear; }
    public void setKekClear(String v) { this.kekClear = v; }
    public String getKekUnderAcqLmk() { return kekUnderAcqLmk; }
    public void setKekUnderAcqLmk(String v) { this.kekUnderAcqLmk = v; }
    public String getKcv() { return kcv; }
    public void setKcv(String v) { this.kcv = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
