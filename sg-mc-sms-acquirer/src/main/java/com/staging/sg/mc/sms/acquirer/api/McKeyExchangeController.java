package com.staging.sg.mc.sms.acquirer.api;

import com.staging.sg.mc.sms.acquirer.entity.McSmsKek;
import com.staging.sg.mc.sms.acquirer.network.McSmsKeyExchange;
import com.staging.sg.mc.sms.acquirer.repository.McSmsKekRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints d'echange de cles Mastercard SMS (cote membre/acquereur).
 *
 *   POST /api/admin/mc/keys/bootstrap-zmk   injecte la ZMK (hors bande)
 *   POST /api/admin/mc/keys/solicit         0800 DE70=162
 *   GET  /api/admin/mc/keys/current         etat des cles
 *
 * La ZMK est injectee hors bande, comme pour SWAM : elle n'est jamais
 * echangee sur le reseau ISO. Chez Mastercard elle est convenue lors de
 * l'onboarding du membre.
 */
@RestController
@RequestMapping("/api/admin/mc/keys")
public class McKeyExchangeController {

    private final McSmsKeyExchange keyExchange;
    private final McSmsKekRepository kekRepo;

    @Value("${mc.sms.member-group-id:MCTESTGRP}")
    private String memberGroupId;

    public McKeyExchangeController(McSmsKeyExchange keyExchange,
                                   McSmsKekRepository kekRepo) {
        this.keyExchange = keyExchange;
        this.kekRepo = kekRepo;
    }

    /**
     * Injecte la ZMK en clair et calcule son KCV.
     *
     * Valeur de l'environnement de test (identique a celle des travaux SWAM) :
     *   13AED5DA1F32347523C708C11F2608FD   KCV 2D617C
     */
    @PostMapping("/bootstrap-zmk")
    public Map<String, Object> bootstrapZmk(@RequestParam String zmk) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            String clean = zmk.trim().toUpperCase();
            if (clean.length() != 32 && clean.length() != 48) {
                r.put("success", false);
                r.put("error", "ZMK attendue en 32 (double) ou 48 (triple) caracteres hex, recu "
                             + clean.length());
                return r;
            }

            String kcv = computeKcv(clean);

            McSmsKek kek = kekRepo.findByMemberGroupId(memberGroupId).orElseGet(McSmsKek::new);
            kek.setMemberGroupId(memberGroupId);
            kek.setKeyLength(clean.length() / 2);
            kek.setKekClear(clean);
            kek.setKcv(kcv.substring(0, 6));   // colonne VARCHAR(6)
            kek.setStatus("ACTIVE");
            kekRepo.save(kek);

            r.put("success", true);
            r.put("member_group_id", memberGroupId);
            r.put("key_length_bytes", clean.length() / 2);
            r.put("kcv", kcv.substring(0, 6));
            r.put("kcv_full", kcv);
        } catch (Exception e) {
            r.put("success", false);
            r.put("error", e.getMessage());
        }
        return r;
    }

    /** Sollicite un echange de cle : 0800 DE70=162. */
    @PostMapping("/solicit")
    public Map<String, Object> solicit() throws Exception {
        return keyExchange.solicitKeyExchange();
    }

    /** Etat courant des cles. */
    @GetMapping("/current")
    public Map<String, Object> current() {
        return keyExchange.currentKey();
    }

    // ------------------------------------------------------------------
    //  KCV : chiffrement de 8 octets nuls avec la cle
    // ------------------------------------------------------------------

    private String computeKcv(String keyHex) throws Exception {
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, tripleDesKey(keyHex));
        byte[] out = c.doFinal(new byte[8]);
        StringBuilder sb = new StringBuilder();
        for (byte b : out) sb.append(String.format("%02X", b));
        return sb.toString();
    }

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

    private static byte[] unhex(String s) {
        int n = s.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}
