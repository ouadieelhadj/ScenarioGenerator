package com.staging.sg.common.service;

import com.staging.sg.common.entity.McDmasInterface;
import com.staging.sg.common.repository.McDmasInterfaceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interfaces pilotees par le module courant, lues dans mc_dmas_interface.
 *
 * ------------------------------------------------------------------
 *  UN MODULE, UNE OU PLUSIEURS INTERFACES
 * ------------------------------------------------------------------
 * Le parametre --sg.interface accepte une liste :
 *
 *     --sg.interface=DMAS_BANK_A                   une seule banque
 *     --sg.interface=DMAS_BANK_A,DMAS_BANK_B       deux banques, une JVM
 *
 * En mode multi, chaque interface garde sa PROPRE liaison permanente :
 * son channel, son thread d'ecoute, son sign-on, ses cles. Une JVM,
 * N sockets ouvertes en continu.
 *
 * Le port REST est celui de la PREMIERE interface de la liste ; les
 * appels precisent ensuite la banque : ?bank=022905
 *
 * ------------------------------------------------------------------
 *  DEUX IDENTIFIANTS A NE PAS CONFONDRE
 * ------------------------------------------------------------------
 *   memberGroupId    cle de recherche EN BASE des KEK et PEK
 *   groupSignonDe2   identifiant du membre SUR LE RESEAU (DE2 des 0800)
 *
 * Les confondre a deja provoque des "KEK absente pour 40260".
 */
@Service
@ConditionalOnProperty(name = "sg.network.code", havingValue = "DMAS")
public class McDmasInterfaceService {

    private static final Logger log = LoggerFactory.getLogger(McDmasInterfaceService.class);

    public static final String OFF           = "OFF";
    public static final String SIGNON        = "SIGNON";
    public static final String PEK_EXCHANGED = "PEK_EXCHANGED";
    public static final String READY         = "READY";
    public static final String SIGNOFF       = "SIGNOFF";

    private final McDmasInterfaceRepository repo;

    /** Une ou plusieurs interfaces, separees par des virgules. */
    @Value("${sg.interface:}")
    private String idInterfaces;

    /** Interfaces pilotees, dans l'ordre de la liste. Cle : bank_code. */
    private final Map<String, McDmasInterface> byBank = new LinkedHashMap<>();

    /** La premiere de la liste : celle qui donne le port REST. */
    private volatile McDmasInterface primary;

    public McDmasInterfaceService(McDmasInterfaceRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void load() {
        if (idInterfaces == null || idInterfaces.isBlank()) {
            throw new IllegalStateException(
                    "[DMAS-IF] Parametre --sg.interface absent. Chaque module DMAS doit "
                  + "declarer son ou ses interfaces, par exemple "
                  + "--sg.interface=DMAS_BANK_A ou --sg.interface=DMAS_BANK_A,DMAS_BANK_B. "
                  + "Valeurs disponibles : SELECT id_interface FROM mc_dmas_interface;");
        }

        for (String id : idInterfaces.split(",")) {
            String key = id.trim();
            if (key.isEmpty()) continue;

            McDmasInterface i = repo.findById(key).orElseThrow(() -> new IllegalStateException(
                    "[DMAS-IF] Interface '" + key + "' introuvable dans mc_dmas_interface"));

            byBank.put(i.getBankCode(), i);
            if (primary == null) primary = i;

            log.info("[DMAS-IF] {} — banque {} ({})",
                    i.getIdInterface(), i.getBankCode(), i.getLabel());
            log.info("[DMAS-IF]   REST {}  ISO {}  cible {}:{}",
                    i.getRestPort(), i.getIsoPort(), i.getTargetHost(), i.getTargetPort());
            log.info("[DMAS-IF]   DE32={} DE33={} DE100={} DE2={} memberGroup={}",
                    i.getAcqIcaDe32(), i.getFwdIdDe33(), i.getIssIcaDe100(),
                    i.getGroupSignonDe2(), i.getMemberGroupId());

            setStatus(i.getBankCode(), OFF);
        }

        if (byBank.size() > 1) {
            log.info("[DMAS-IF] Mode MULTI-BANQUE : {} interfaces, port REST {} (la premiere)",
                    byBank.size(), primary.getRestPort());
        }
    }

    @PreDestroy
    public void shutdown() {
        byBank.keySet().forEach(b -> setStatus(b, OFF));
        log.info("[DMAS-IF] {} interface(s) arretee(s)", byBank.size());
    }

    // ==================================================================
    //  ACCES
    // ==================================================================

    /** Toutes les interfaces pilotees. */
    public List<McDmasInterface> all() {
        return new ArrayList<>(byBank.values());
    }

    public boolean isMulti() { return byBank.size() > 1; }

    /** Codes banque pilotes. */
    public List<String> bankCodes() {
        return new ArrayList<>(byBank.keySet());
    }

    /**
     * Interface d'une banque donnee. Si bankCode est vide, retourne la
     * principale — ce qui permet aux appels REST d'omettre ?bank= quand
     * le module ne pilote qu'une interface.
     */
    public McDmasInterface byBank(String bankCode) {
        if (bankCode == null || bankCode.isBlank()) {
            return self();
        }
        McDmasInterface i = byBank.get(bankCode.trim());
        if (i == null) {
            throw new IllegalArgumentException(
                    "Banque '" + bankCode + "' non pilotee par ce module. Disponibles : "
                  + String.join(", ", byBank.keySet()));
        }
        return i;
    }

    /** Interface principale : la premiere de la liste. */
    public McDmasInterface self() {
        if (primary == null) {
            throw new IllegalStateException("Aucune interface chargee");
        }
        return primary;
    }

    // --- raccourcis sur l'interface principale (mode mono) ---

    public String bankCode()       { return self().getBankCode(); }
    public String memberGroupId()  { return self().getMemberGroupId(); }
    public String groupSignonDe2() { return self().getGroupSignonDe2(); }
    public String fwdIdDe33()      { return self().getFwdIdDe33(); }
    public String acqIcaDe32()     { return self().getAcqIcaDe32(); }
    public String issIcaDe100()    { return self().getIssIcaDe100(); }

    // --- les memes, pour une banque precise ---

    public String memberGroupId(String bankCode)  { return byBank(bankCode).getMemberGroupId(); }
    public String groupSignonDe2(String bankCode) { return byBank(bankCode).getGroupSignonDe2(); }
    public String fwdIdDe33(String bankCode)      { return byBank(bankCode).getFwdIdDe33(); }

    // ==================================================================
    //  STATUT
    // ==================================================================

    /** Etat courant de l'interface principale. */
    public String status() {
        return status(bankCode());
    }

    public String status(String bankCode) {
        McDmasInterface i = byBank(bankCode);
        return repo.findById(i.getIdInterface())
                   .map(McDmasInterface::getStatus).orElse(OFF);
    }

    public void setStatus(String status) {
        setStatus(bankCode(), status);
    }

    /**
     * Met a jour l'etat d'une interface.
     * PEK_EXCHANGED entraine automatiquement le passage a READY.
     */
    @Transactional
    public void setStatus(String bankCode, String status) {
        try {
            McDmasInterface ref = byBank.get(bankCode);
            if (ref == null) return;

            McDmasInterface i = repo.findById(ref.getIdInterface()).orElse(null);
            if (i == null) return;

            i.setStatus(status);
            i.setStatusUpdated(LocalDateTime.now());
            repo.save(i);
            byBank.put(bankCode, i);
            if (primary != null && primary.getBankCode().equals(bankCode)) primary = i;
            log.info("[DMAS-IF] {} statut -> {}", bankCode, status);

            if (PEK_EXCHANGED.equals(status)) {
                i.setStatus(READY);
                i.setStatusUpdated(LocalDateTime.now());
                repo.save(i);
                byBank.put(bankCode, i);
                if (primary != null && primary.getBankCode().equals(bankCode)) primary = i;
                log.info("[DMAS-IF] {} statut -> {} (cle echangee)", bankCode, READY);
            }
        } catch (Exception e) {
            log.warn("[DMAS-IF] Mise a jour du statut impossible : {}", e.getMessage());
        }
    }

    /**
     * Bascule a OFF une interface que ce module ne pilote pas. Utilise par
     * le Mastercard quand la socket d'un membre tombe : c'est lui qui le
     * constate en premier.
     */
    /** Pose un statut sur une interface que ce module ne pilote pas. */
    @Transactional
    public void markStatus(String idInterface, String status) {
        try {
            repo.findById(idInterface).ifPresent(i -> {
                i.setStatus(status);
                i.setStatusUpdated(LocalDateTime.now());
                repo.save(i);
                log.info("[DMAS-IF] {} -> {}", idInterface, status);
            });
        } catch (Exception e) {
            log.warn("[DMAS-IF] Mise a jour impossible : {}", e.getMessage());
        }
    }

    @Transactional
    public void markOff(String idInterface) {
        try {
            repo.findById(idInterface).ifPresent(i -> {
                i.setStatus(OFF);
                i.setStatusUpdated(LocalDateTime.now());
                repo.save(i);
                log.info("[DMAS-IF] {} bascule a OFF", idInterface);
            });
        } catch (Exception e) {
            log.warn("[DMAS-IF] Bascule a OFF impossible : {}", e.getMessage());
        }
    }

    /** Retrouve n'importe quelle interface en base, pilotee ou non. */
    public McDmasInterface lookupByBankCode(String bankCode) {
        return repo.findByBankCode(bankCode).orElse(null);
    }

    /**
     * Identifie le membre a partir du DE2 d'un 0800 (Group Sign-on ID).
     *
     * C'est ainsi que le Mastercard sait a quelle banque il parle :
     * il recoit DE2=40260 et en deduit la banque 022905, dont le
     * member_group_id est TESTGRP01 — la cle de recherche des cles.
     */
    public McDmasInterface lookupByGroupSignon(String de2) {
        if (de2 == null || de2.isBlank()) return null;
        return repo.findByGroupSignonDe2(de2.trim()).orElse(null);
    }

    /**
     * member_group_id du membre identifie par le DE2 recu. Repli sur
     * celui de l'interface principale si le DE2 est inconnu.
     */
    public String memberGroupIdForDe2(String de2) {
        McDmasInterface i = lookupByGroupSignon(de2);
        return (i != null && i.getMemberGroupId() != null)
                ? i.getMemberGroupId()
                : memberGroupId();
    }
}
