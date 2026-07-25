package com.staging.sg.common.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Interface DMAS : une ligne par banque et par Mastercard.
 *
 * Remplace la table `networks` pour DMAS. Chaque module recoit un seul
 * parametre au demarrage :
 *
 *     java -jar sg-mc-dmas-member.jar --sg.interface=DMAS_BANK_A
 *
 * et lit ici tout le reste : identifiants ISO et ports.
 *
 * ------------------------------------------------------------------
 *  DEUX IDENTIFIANTS A NE PAS CONFONDRE
 * ------------------------------------------------------------------
 *   memberGroupId    cle de recherche EN BASE des KEK et PEK
 *   groupSignonDe2   identifiant du membre SUR LE RESEAU (DE2 des 0800)
 *
 * Les confondre a deja provoque des "KEK absente pour 40260".
 */
@Entity
@Table(name = "mc_dmas_interface")
public class McDmasInterface {

    /** Valeur passee au demarrage via --sg.interface. */
    @Id
    @Column(name = "id_interface", length = 32)
    private String idInterface;

    /** Six chiffres, au format ICA. Unique. */
    @Column(name = "bank_code", nullable = false, length = 6)
    private String bankCode;

    @Column(name = "label", length = 64)
    private String label;

    // --- role acquereur ---

    /** DE32 : identifie l'institution acquereuse. */
    @Column(name = "acq_ica_de32", length = 11)
    private String acqIcaDe32;

    @Column(name = "acq_arid", length = 11)
    private String acqArid;

    // --- role emetteur ---

    /** DE100 : identifie l'institution destinataire. */
    @Column(name = "iss_ica_de100", length = 11)
    private String issIcaDe100;

    @Column(name = "iss_arid", length = 11)
    private String issArid;

    // --- commun ---

    /** DE33 : six chiffres, identifie qui route le message vers DMAS. */
    @Column(name = "fwd_id_de33", length = 11)
    private String fwdIdDe33;

    /** DE2 des messages 0800. */
    @Column(name = "group_signon_de2", length = 11)
    private String groupSignonDe2;

    /** Cle d'indexation des KEK et PEK en base. */
    @Column(name = "member_group_id", length = 32)
    private String memberGroupId;

    /** ACQUIRER, ISSUER ou BOTH. */
    @Column(name = "business_role", length = 16)
    private String businessRole;

    // --- transport ---

    @Column(name = "host", length = 64)
    private String host;

    @Column(name = "rest_port")
    private Integer restPort;

    /** Renseigne uniquement cote Mastercard, qui ecoute. */
    @Column(name = "iso_port")
    private Integer isoPort;

    /** Ou ce module se connecte : un autre module ou un vrai MIP. */
    @Column(name = "target_host", length = 64)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "log_file", length = 500)
    private String logFile;

    // --- etat ---

    /** OFF, SIGNON, PEK_EXCHANGED, READY, SIGNOFF. */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "status_updated")
    private LocalDateTime statusUpdated;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ------------------------------------------------------------------

    public String getIdInterface()    { return idInterface; }
    public String getBankCode()       { return bankCode; }
    public String getLabel()          { return label; }
    public String getAcqIcaDe32()     { return acqIcaDe32; }
    public String getAcqArid()        { return acqArid; }
    public String getIssIcaDe100()    { return issIcaDe100; }
    public String getIssArid()        { return issArid; }
    public String getFwdIdDe33()      { return fwdIdDe33; }
    public String getGroupSignonDe2() { return groupSignonDe2; }
    public String getMemberGroupId()  { return memberGroupId; }
    public String getBusinessRole()   { return businessRole; }
    public String getHost()           { return host; }
    public Integer getRestPort()      { return restPort; }
    public Integer getIsoPort()       { return isoPort; }
    public String getTargetHost()     { return targetHost; }
    public Integer getTargetPort()    { return targetPort; }
    public String getLogFile()        { return logFile; }
    public String getStatus()         { return status; }
    public LocalDateTime getStatusUpdated() { return statusUpdated; }
    public Boolean getActive()        { return active; }

    public void setIdInterface(String v)    { this.idInterface = v; }
    public void setBankCode(String v)       { this.bankCode = v; }
    public void setLabel(String v)          { this.label = v; }
    public void setAcqIcaDe32(String v)     { this.acqIcaDe32 = v; }
    public void setAcqArid(String v)        { this.acqArid = v; }
    public void setIssIcaDe100(String v)    { this.issIcaDe100 = v; }
    public void setIssArid(String v)        { this.issArid = v; }
    public void setFwdIdDe33(String v)      { this.fwdIdDe33 = v; }
    public void setGroupSignonDe2(String v) { this.groupSignonDe2 = v; }
    public void setMemberGroupId(String v)  { this.memberGroupId = v; }
    public void setBusinessRole(String v)   { this.businessRole = v; }
    public void setHost(String v)           { this.host = v; }
    public void setRestPort(Integer v)      { this.restPort = v; }
    public void setIsoPort(Integer v)       { this.isoPort = v; }
    public void setTargetHost(String v)     { this.targetHost = v; }
    public void setTargetPort(Integer v)    { this.targetPort = v; }
    public void setLogFile(String v)        { this.logFile = v; }
    public void setStatus(String v)         { this.status = v; }
    public void setStatusUpdated(LocalDateTime v) { this.statusUpdated = v; }
    public void setActive(Boolean v)        { this.active = v; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }

    /** true si ce module ecoute (Mastercard) plutot que de se connecter. */
    public boolean isListener() {
        return isoPort != null && (targetHost == null || targetHost.isBlank());
    }

    @Override
    public String toString() {
        return "McDmasInterface{" + idInterface + " bank=" + bankCode
             + " rest=" + restPort + " iso=" + isoPort
             + " target=" + targetHost + ":" + targetPort
             + " status=" + status + "}";
    }
}
