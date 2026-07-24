package com.staging.sg.mc.dmas.mastercard.api;

import com.staging.sg.common.entity.McDmasMastercardKey;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.McDmasMastercardKeyRepository;
import com.staging.sg.common.service.McDmasInterfaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap de la MDK (Master Derivation Key) cote RESEAU (pour valider l ARQC plus tard).
 *
 * La MDK est la cle maitre emetteur d'ou derive, pour chaque carte, la
 * cle ICC puis la cle de session qui produit l'ARQC. C'est le membre
 * qui construit l'ARQC : il a donc besoin de la MDK en base.
 *
 * Elle est stockee chiffree sous LMK, comme toutes les cles — jamais en
 * clair. Le body fournit la valeur claire, le HSM la forme sous LMK.
 *
 *   POST /api/admin/dmas/mdk/bootstrap
 *   { "mdkClear":"6E46FE40...5E73", "bank":"022905" }
 *
 * Jeu de test Visa : MDK1 = 6E46FE409DF704BCA75E7FF270B65E73, KCV 944A44.
 */
@RestController
@RequestMapping("/api/admin/dmas/mdk")
public class MdkBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(MdkBootstrapController.class);

    private final JposHsmService hsm;
    private final McDmasMastercardKeyRepository keyRepo;
    private final McDmasInterfaceService iface;

    public MdkBootstrapController(JposHsmService hsm,
                                  McDmasMastercardKeyRepository keyRepo,
                                  McDmasInterfaceService iface) {
        this.hsm = hsm;
        this.keyRepo = keyRepo;
        this.iface = iface;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody Map<String, String> body) {
        try {
            String mdkClear = body.get("mdkClear");
            // Cote reseau, la MDK est indexee sur le member_group_id DU MEMBRE
            // (ex. TESTGRP01), pas sur la banque du Mastercard. Le membre est
            // identifie soit par memberGroupId direct, soit deduit du DE2.
            String mgid = body.get("memberGroupId");
            String bank = body.get("bank");
            if (mdkClear == null || mdkClear.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "mdkClear requis"));
            }
            // Repli : si bank fournie et pilotee, en deduire le member_group_id ;
            // sinon si un DE2 est donne, le resoudre ; sinon memberGroupId direct.
            if (mgid == null || mgid.isBlank()) {
                if (bank != null && !bank.isBlank()) {
                    var found = iface.lookupByBankCode(bank);
                    mgid = (found != null) ? found.getMemberGroupId() : bank;
                } else {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "memberGroupId requis (ou bank connue)"));
                }
            }
            String bankCode = bank;
            String label    = "MDK-" + (bank != null ? bank : mgid);

            JposHsmService.KekUnderLmk formed = hsm.formKekUnderLmk(mdkClear);

            McDmasMastercardKey k = keyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, "MDK", "ACTIVE")
                    .orElseGet(McDmasMastercardKey::new);
            k.setMemberGroupId(mgid);
            k.setBankCode(bankCode);
            k.setKeyType("MDK");
            k.setKeyLabel(label);
            k.setKeyLength(mdkClear.length() / 2);
            k.setKeyUnderLmk(formed.underLmkHex);
            k.setKcv(formed.kcv);
            k.setStatus("ACTIVE");
            keyRepo.save(k);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("side", "MASTERCARD");
            r.put("bank_code", bankCode);
            r.put("member_group_id", mgid);
            r.put("key_label", label);
            r.put("kcv", formed.kcv);
            r.put("status", "ACTIVE");
            log.info("[MDK-BOOT] Reseau — {} KCV={}", label, formed.kcv);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[MDK-BOOT] echec : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
