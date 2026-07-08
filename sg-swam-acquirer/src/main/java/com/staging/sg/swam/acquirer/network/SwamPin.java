package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.repository.SwamAcqKeyRepository;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PIN block SWAM cote MEMBRE : chiffre le PIN sous ZPK (ISO-0/Format 0)
 * et pose DE52 (bloc chiffre 8o) + DE53 sur le message.
 *
 * DE53 = "0201000000" (section 16 SESSION_RESUME) :
 *   pos1-2 = 02 (ZPK encryption)
 *   pos3-4 = 01 (ANSI X9.8 ISO-0 Format 0)
 *   pos5-7 = 000 (index cle PIN, reserve)
 *   pos8-10= 000 (index cle MAC, reserve)
 *
 * La ZPK est double longueur (16o) -> rebuildKey -> jposLen(16)=128 -> correct jPOS.
 * Utilise getKeyUnderLmk() comme DMAS (encryptPinBlock attend la cle sous LMK).
 */
@Service
public class SwamPin {

    private static final Logger log = LoggerFactory.getLogger(SwamPin.class);
    private static final String MGID = "TESTGRP01";

    private final HsmService hsm;
    private final SwamAcqKeyRepository acqKeyRepo;

    public SwamPin(HsmService hsm, SwamAcqKeyRepository acqKeyRepo) {
        this.hsm = hsm; this.acqKeyRepo = acqKeyRepo;
    }

    /** Chiffre pin, pose DE52+DE53. Sans effet si pin=null ou ZPK absente. */
    public void apply(ISOMsg m, String pin) throws Exception {
        if (pin == null || pin.isEmpty()) return;
        SwamAcqKey pek = acqKeyRepo.findByMemberGroupIdAndKeyTypeAndStatus(MGID, "PEK", "ACTIVE").orElse(null);
        if (pek == null) { log.warn("[SWAM-PIN] ZPK absente -> pas de DE52"); return; }
        String pan = m.hasField(2) ? m.getString(2) : "";
        byte[] pinBlock = hsm.encryptPinBlock(
                pin, pan,
                pek.getKeyUnderLmk(), pek.getKcv(), pek.getKeyLength());
        m.set(52, pinBlock);
        m.set(53, "0201000000");
        log.info("[SWAM-PIN] DE52 pose ({}o) + DE53=0201000000 (ZPK kcv={})", pinBlock.length, pek.getKcv());
    }
}
