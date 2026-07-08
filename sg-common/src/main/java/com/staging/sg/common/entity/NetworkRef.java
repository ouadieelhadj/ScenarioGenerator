package com.staging.sg.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Referentiel d'un reseau / interface monetique (table networks).
 * Nomme NetworkRef pour ne pas entrer en collision avec l'utilitaire
 * reseau existant NetworkUtil.
 */
@Entity
@Table(name = "networks")
public class NetworkRef {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    // ----- Famille A : protocole -----
    @Column(name = "iso_version", length = 20)            private String isoVersion;
    @Column(name = "length_prefix_size")                  private Integer lengthPrefixSize;
    @Column(name = "length_prefix_encoding", length = 10) private String lengthPrefixEncoding;
    @Column(name = "header_type", length = 20)            private String headerType;
    @Column(name = "default_field_encoding", length = 10) private String defaultFieldEncoding;
    @Column(name = "mac_present")                         private Boolean macPresent;
    @Column(name = "pin_block_format", length = 20)       private String pinBlockFormat;
    @Column(name = "packager_class", length = 255)        private String packagerClass;

    // ----- Famille B : infra -----
    @Column(name = "acquirer_host", length = 100)         private String acquirerHost;
    @Column(name = "acquirer_rest_port")                  private Integer acquirerRestPort;
    @Column(name = "acquirer_jpos_port")                  private Integer acquirerJposPort;
    @Column(name = "issuer_host", length = 100)           private String issuerHost;
    @Column(name = "issuer_rest_port")                    private Integer issuerRestPort;
    @Column(name = "issuer_iso_port")                     private Integer issuerIsoPort;
    @Column(name = "orchestrator_port")                   private Integer orchestratorPort;

    @Column(nullable = false)                             private boolean active = true;
    @Column(name = "created_at")                          private LocalDateTime createdAt = LocalDateTime.now();

    public NetworkRef() {}

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getIsoVersion() { return isoVersion; }
    public void setIsoVersion(String v) { this.isoVersion = v; }
    public Integer getLengthPrefixSize() { return lengthPrefixSize; }
    public void setLengthPrefixSize(Integer v) { this.lengthPrefixSize = v; }
    public String getLengthPrefixEncoding() { return lengthPrefixEncoding; }
    public void setLengthPrefixEncoding(String v) { this.lengthPrefixEncoding = v; }
    public String getHeaderType() { return headerType; }
    public void setHeaderType(String v) { this.headerType = v; }
    public String getDefaultFieldEncoding() { return defaultFieldEncoding; }
    public void setDefaultFieldEncoding(String v) { this.defaultFieldEncoding = v; }
    public Boolean getMacPresent() { return macPresent; }
    public void setMacPresent(Boolean v) { this.macPresent = v; }
    public String getPinBlockFormat() { return pinBlockFormat; }
    public void setPinBlockFormat(String v) { this.pinBlockFormat = v; }
    public String getPackagerClass() { return packagerClass; }
    public void setPackagerClass(String v) { this.packagerClass = v; }
    public String getAcquirerHost() { return acquirerHost; }
    public void setAcquirerHost(String v) { this.acquirerHost = v; }
    public Integer getAcquirerRestPort() { return acquirerRestPort; }
    public void setAcquirerRestPort(Integer v) { this.acquirerRestPort = v; }
    public Integer getAcquirerJposPort() { return acquirerJposPort; }
    public void setAcquirerJposPort(Integer v) { this.acquirerJposPort = v; }
    public String getIssuerHost() { return issuerHost; }
    public void setIssuerHost(String v) { this.issuerHost = v; }
    public Integer getIssuerRestPort() { return issuerRestPort; }
    public void setIssuerRestPort(Integer v) { this.issuerRestPort = v; }
    public Integer getIssuerIsoPort() { return issuerIsoPort; }
    public void setIssuerIsoPort(Integer v) { this.issuerIsoPort = v; }
    public Integer getOrchestratorPort() { return orchestratorPort; }
    public void setOrchestratorPort(Integer v) { this.orchestratorPort = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
