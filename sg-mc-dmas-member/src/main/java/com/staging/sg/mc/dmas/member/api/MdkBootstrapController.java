package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.repository.McDmasMemberKeyRepository;
import com.staging.sg.common.service.McDmasInterfaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap de la MDK (Master Derivation Key) cote MEMBRE.
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
    private final McDmasMemberKeyRepository keyRepo;
    private final McDmasInterfaceService iface;

    public MdkBootstrapController(JposHsmService hsm,
                                  McDmasMemberKeyRepository keyRepo,
                                  McDmasInterfaceService iface) {
        this.hsm = hsm;
        this.keyRepo = keyRepo;
        this.iface = iface;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@RequestBody Map<String, String> body) {
        try {
            String mdkClear = body.get("mdkClear");
            String bank     = body.get("bank");
            if (mdkClear == null || mdkClear.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "mdkClear requis"));
            }

            var cfg  = iface.byBank(bank);
            String mgid      = cfg.getMemberGroupId();
            String bankCode  = cfg.getBankCode();
            String label     = "MDK-" + bankCode;

            JposHsmService.KekUnderLmk formed = hsm.formKekUnderLmk(mdkClear);

            McDmasMemberKey k = keyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(mgid, "MDK", "ACTIVE")
                    .orElseGet(McDmasMemberKey::new);
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
            r.put("side", "MEMBER");
            r.put("bank_code", bankCode);
            r.put("member_group_id", mgid);
            r.put("key_label", label);
            r.put("kcv", formed.kcv);
            r.put("status", "ACTIVE");
            log.info("[MDK-BOOT] Membre — {} KCV={}", label, formed.kcv);
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[MDK-BOOT] echec : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
