package com.staging.sg.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Configuration d'une connexion membre SWAM ou reseau SWAM. */
@Entity
@Table(name = "swam_interface")
public class SwamInterface {

    @Id
    @Column(name = "id_interface", length = 32)
    private String idInterface;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "acquirer_code_de32", length = 20)
    private String acquirerCodeDe32;

    @Column(name = "issuer_code_de33", length = 20)
    private String issuerCodeDe33;

    @Column(name = "member_group_id", length = 32)
    private String memberGroupId;

    @Column(name = "business_role", length = 16)
    private String businessRole;

    @Column(name = "host", length = 100)
    private String host;

    @Column(name = "rest_port")
    private Integer restPort;

    @Column(name = "iso_port")
    private Integer isoPort;

    @Column(name = "target_host", length = 100)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "log_file", length = 500)
    private String logFile;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getIdInterface() { return idInterface; }
    public void setIdInterface(String v) { this.idInterface = v; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String v) { this.bankCode = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public String getAcquirerCodeDe32() { return acquirerCodeDe32; }
    public void setAcquirerCodeDe32(String v) { this.acquirerCodeDe32 = v; }
    public String getIssuerCodeDe33() { return issuerCodeDe33; }
    public void setIssuerCodeDe33(String v) { this.issuerCodeDe33 = v; }
    public String getMemberGroupId() { return memberGroupId; }
    public void setMemberGroupId(String v) { this.memberGroupId = v; }
    public String getBusinessRole() { return businessRole; }
    public void setBusinessRole(String v) { this.businessRole = v; }
    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }
    public Integer getRestPort() { return restPort; }
    public void setRestPort(Integer v) { this.restPort = v; }
    public Integer getIsoPort() { return isoPort; }
    public void setIsoPort(Integer v) { this.isoPort = v; }
    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String v) { this.targetHost = v; }
    public Integer getTargetPort() { return targetPort; }
    public void setTargetPort(Integer v) { this.targetPort = v; }
    public String getLogFile() { return logFile; }
    public void setLogFile(String v) { this.logFile = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
