package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.SwamMacBuilder;
import com.staging.sg.common.repository.SwamAcqKeyRepository;
import com.staging.sg.common.repository.SwamKekRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * MAC SWAM cote MEMBRE : pose le DE128 sur les messages sortants.
 *
 * MODE REEL HPS (section 20.7 du SESSION_RESUME) — aligne sur SwamJposServer :
 *   - cle    = la ZMK utilisee comme TAK (double longueur). Il n'y a PAS de ZAK.
 *   - algo   = 3DES-CBC-MAC (ISO 9797 Algorithm 1), padding Method 1 (zeros).
 *   - donnee = message packe SANS MTI, SANS bitmap, SANS DE128 (cf SwamMacBuilder).
 *   - DE128  = les swam.mac.length premiers octets du MAC (4 en reel HPS).
 *
 * L'ancien mode (ZAK simple longueur + McMacBuilder sur les champs 4,11,37,41,42
 * + DE128 8 octets) est ABANDONNE : le vrai membre ne fonctionne pas comme ca.
 */
@Service
public class SwamMac {

    private static final Logger log = LoggerFactory.getLogger(SwamMac.class);
    private static final String MGID = "TESTGRP01";
    private static final int MAC_FIELD = 128;

    private final JposHsmService hsm;
    private final SwamAcqKeyRepository acqKeyRepo;   // conserve (autres usages eventuels)
    private final SwamKekRepository kekRepo;

    /** Longueur du DE128 en octets. DOIT matcher SwamPackager (IFA_BINARY(4)). */
    @Value("${swam.mac.length:4}") private int macLength;

    public SwamMac(JposHsmService hsm, SwamAcqKeyRepository acqKeyRepo, SwamKekRepository kekRepo) {
        this.hsm = hsm; this.acqKeyRepo = acqKeyRepo; this.kekRepo = kekRepo;
    }

    /** Calcule et pose le DE128. Renvoie le MAC hex pose (ou null si aucune cle). */
    public String apply(ISOMsg m) throws Exception {
        byte[] input = SwamMacBuilder.build(m);
        byte[] full;
        String mti = m.getMTI();
        String function = m.hasField(24) ? m.getString(24) : null;
        boolean zmkMandatory = "1804".equals(mti) || "1814".equals(mti);

        SwamAcqKey mak = acqKeyRepo
                .findByMemberGroupIdAndKeyTypeAndStatus(MGID, "MAK", "ACTIVE")
                .orElse(null);

        if (!zmkMandatory && mak != null && mak.getKeyUnderLmk() != null) {
            full = hsm.generateMac(input, mak.getKeyUnderLmk(), mak.getKcv(), mak.getKeyLength());
            log.info("[SWAM-MAC] MAC avec ZAK (MAK, KCV={})", mak.getKcv());
        } else {
            SwamKek kek = kekRepo.findByMemberGroupId(MGID).orElse(null);
            if (kek == null || kek.getKekClear() == null) {
                log.warn("[SWAM-MAC] ni ZAK ni ZMK claire -> pas de DE128");
                return null;
            }
            full = hsm.generateMacZmk(input, kek.getKekClear());
            log.info("[SWAM-MAC] MAC avec ZMK (MTI={} DE24={})",
                    mti, function);
        }

        byte[] mac = (macLength > 0 && macLength < full.length)
                ? Arrays.copyOfRange(full, 0, macLength)
                : full;

        m.set(MAC_FIELD, mac);
        String hex = ISOUtil.hexString(mac);
        log.info("[SWAM-MAC] DE128 pose ({} octets) mac={} (mac8={})",
                mac.length, hex, ISOUtil.hexString(full));
        return hex;
    }

    /**
     * Verifie le DE128 d'un message recu du switch (reponse 1814 / 1110).
     * @return true si OK ou si rien a verifier (tolerant), false si MAC invalide.
     */
    public boolean verify(ISOMsg m) {
        try {
            if (!m.hasField(MAC_FIELD)) return true;   // pas de MAC -> rien a verifier
            SwamKek kek = kekRepo.findByMemberGroupId(MGID).orElse(null);
            if (kek == null || kek.getKekClear() == null) {
                log.warn("[SWAM-MAC] ZMK claire absente -> MAC entrant non verifie");
                return true;
            }
            byte[] input = SwamMacBuilder.build(m);
            byte[] rxMac = m.getBytes(MAC_FIELD);
            boolean ok = hsm.verifyMacZmk(input, kek.getKekClear(), rxMac);
            log.info("[SWAM-MAC] Verif DE128 entrant ({} octets) -> {}", rxMac.length, ok ? "OK" : "FAIL");
            return ok;
        } catch (Exception e) {
            log.error("[SWAM-MAC] verify erreur : {}", e.getMessage(), e);
            return false;
        }
    }
}
