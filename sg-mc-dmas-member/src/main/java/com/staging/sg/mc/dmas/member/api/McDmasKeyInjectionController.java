package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.entity.McDmasKek;
import com.staging.sg.common.entity.McDmasMemberKey;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.McDmasKekRepository;
import com.staging.sg.common.repository.McDmasMemberKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Injection MANUELLE de cles cote MEMBRE, sans passer par le reseau.
 *
 * Trois chemins menent a la meme table `mc_dmas_member_keys` :
 *
 *   1. injection manuelle   <- ce controleur
 *   2. echange 162          <- le reseau pousse la cle (a venir)
 *   3. push par le membre   <- McDmasKeyExchange.exchangePek (existant)
 *
 * Une transaction 0100 dechiffrera donc le PIN avec la meme cle, quelle
 * que soit la facon dont elle est arrivee.
 *
 * Difference de cycle de vie : l'injection manuelle passe directement en
 * ACTIVE, alors que l'echange 162 passe par RECEIVED en attendant le 0820.
 *
 *   POST /api/admin/dmas/keys/inject?clear=<32|48 hex>
 *   POST /api/admin/dmas/keys/inject?underZmk=<32|48 hex>&kcv=<hex>
 *   GET  /api/admin/dmas/keys/current
 */
@RestController
@RequestMapping("/api/admin/dmas/keys")
public class McDmasKeyInjectionController {

    private static final Logger log =
            LoggerFactory.getLogger(McDmasKeyInjectionController.class);

    private final HsmService hsm;
    private final McDmasKekRepository kekRepo;
    private final McDmasMemberKeyRepository keyRepo;

    public McDmasKeyInjectionController(HsmService hsm,
                                        McDmasKekRepository kekRepo,
                                        McDmasMemberKeyRepository keyRepo) {
        this.hsm = hsm;
        this.kekRepo = kekRepo;
        this.keyRepo = keyRepo;
    }

    /**
     * Injecte une PEK directement en base.
     *
     * Deux formats acceptes, exclusifs :
     *   clear    = la cle EN CLAIR ; elle est chiffree sous la ZMK avant import
     *   underZmk = la cle DEJA CHIFFREE sous la ZMK (cas reel)
     *
     * Dans les deux cas elle est importee sous le LMK local par le HSM,
     * puis persistee avec les deux formes et son KCV.
     */
    @PostMapping("/inject")
    public ResponseEntity<?> inject(
            @RequestParam(required = false) String clear,
            @RequestParam(required = false) String underZmk,
            @RequestParam(required = false) String kcv,
            @RequestParam(defaultValue = "PEK") String keyType,
            @RequestParam(defaultValue = "TESTGRP01") String memberGroupId) {

        Map<String, Object> r = new LinkedHashMap<>();
        try {
            if ((clear == null || clear.isBlank()) && (underZmk == null || underZmk.isBlank())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Fournir soit clear=<hex>, soit underZmk=<hex>"));
            }
            if (clear != null && !clear.isBlank() && underZmk != null && !underZmk.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "clear et underZmk sont exclusifs"));
            }

            McDmasKek kek = kekRepo.findByMemberGroupId(memberGroupId).orElse(null);
            if (kek == null || kek.getKekClear() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "KEK absente pour " + memberGroupId
                               + " — faire le bootstrap avant l'injection"));
            }

            // Si la cle est fournie en clair, on la chiffre sous la KEK
            String keyUnderKek;
            if (clear != null && !clear.isBlank()) {
                String c = clear.trim().toUpperCase();
                if (c.length() != 32 && c.length() != 48) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Cle attendue en 32 (double) ou 48 (triple) hex, recu "
                                   + c.length()));
                }
                keyUnderKek = encryptUnderKek(c, kek.getKekClear());
                r.put("source", "clair, chiffree sous KEK");
            } else {
                keyUnderKek = underZmk.trim().toUpperCase();
                r.put("source", "deja chiffree sous KEK");
            }

            int keyLen = keyUnderKek.length() / 2;

            // Import sous LMK local
            HsmService.KeyResult imp =
                    hsm.importWorkingKey(keyType, keyUnderKek, kek.getKekClear(), keyLen);

            // Verification du KCV si l'appelant en a fourni un
            if (kcv != null && !kcv.isBlank()) {
                String attendu = kcv.trim();
                String calcule = imp.kcv == null ? "" : imp.kcv;
                int n = Math.min(attendu.length(), calcule.length());
                boolean ok = n > 0 && calcule.regionMatches(true, 0, attendu, 0, n);
                r.put("kcv_fourni", attendu);
                r.put("kcv_match", ok);
                if (!ok) {
                    r.put("kcv_calcule", calcule);
                    r.put("error", "KCV different — cle NON persistee");
                    log.error("[DMAS-KEY] KCV different : fourni={} calcule={}", attendu, calcule);
                    return ResponseEntity.badRequest().body(r);
                }
            }

            // Retirer l'ancienne cle active du meme type
            keyRepo.findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, keyType, "ACTIVE")
                    .ifPresent(old -> {
                        old.setStatus("RETIRED");
                        keyRepo.save(old);
                        log.info("[DMAS-KEY] Ancienne {} retiree (KCV={})", keyType, old.getKcv());
                    });

            McDmasMemberKey k = new McDmasMemberKey();
            k.setMemberGroupId(memberGroupId);
            k.setKeyType(keyType);
            k.setKeyLength(keyLen);
            k.setKeyUnderLmk(imp.keyUnderLmkHex);
            k.setKeyUnderKek(keyUnderKek.length() > 64 ? keyUnderKek.substring(0, 64) : keyUnderKek);
            k.setKcv(imp.kcv);
            k.setStatus("ACTIVE");
            keyRepo.save(k);

            log.info("[DMAS-KEY] {} injectee manuellement — KCV={} ({} octets), statut ACTIVE",
                    keyType, imp.kcv, keyLen);

            r.put("success", true);
            r.put("key_type", keyType);
            r.put("member_group_id", memberGroupId);
            r.put("key_length_bytes", keyLen);
            r.put("kcv", imp.kcv);
            r.put("status", "ACTIVE");
            r.put("table", "mc_dmas_member_keys");
            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("[DMAS-KEY] Echec de l'injection : {}", e.getMessage(), e);
            r.put("success", false);
            r.put("error", String.valueOf(e.getMessage()));
            return ResponseEntity.internalServerError().body(r);
        }
    }

    /** Etat des cles du membre. */
    @GetMapping("/current")
    public ResponseEntity<?> current(
            @RequestParam(defaultValue = "TESTGRP01") String memberGroupId,
            @RequestParam(defaultValue = "PEK") String keyType) {

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("member_group_id", memberGroupId);
        r.put("key_type", keyType);
        r.put("table", "mc_dmas_member_keys");

        keyRepo.findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, keyType, "ACTIVE")
                .ifPresentOrElse(k -> {
                    r.put("status", "ACTIVE");
                    r.put("kcv", k.getKcv());
                    r.put("key_length_bytes", k.getKeyLength());
                }, () -> keyRepo
                        .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, keyType, "RECEIVED")
                        .ifPresentOrElse(k -> {
                            r.put("status", "RECEIVED");
                            r.put("kcv", k.getKcv());
                            r.put("note", "en attente du 0820 pour activation");
                        }, () -> r.put("status", "NONE")));

        kekRepo.findByMemberGroupId(memberGroupId).ifPresent(k -> {
            r.put("kek_presente", k.getKekClear() != null);
            r.put("kek_kcv", k.getKcv());
        });
        return ResponseEntity.ok(r);
    }

    // ------------------------------------------------------------------
    //  Chiffrement 3DES-ECB sous la KEK
    // ------------------------------------------------------------------

    private String encryptUnderKek(String clearHex, String kekHex) throws Exception {
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, tripleDesKey(kekHex));
        return hex(c.doFinal(unhex(clearHex)));
    }

    /** Une cle double longueur (16 octets) est etendue en K1|K2|K1. */
    private SecretKey tripleDesKey(String hexKey) throws Exception {
        byte[] k = unhex(hexKey);
        if (k.length == 16) {
            byte[] k24 = new byte[24];
            System.arraycopy(k, 0, k24, 0, 16);
            System.arraycopy(k, 0, k24, 16, 8);
            k = k24;
        }
        return SecretKeyFactory.getInstance("DESede")
                .generateSecret(new DESedeKeySpec(k));
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        int n = s.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}
