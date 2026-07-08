package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamAcqKey;
import com.staging.sg.common.entity.SwamKek;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.crypto.McMacBuilder;
import com.staging.sg.common.repository.SwamAcqKeyRepository;
import com.staging.sg.common.repository.SwamKekRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MAC SWAM cote MEMBRE : pose le DE128 sur les messages sortants.
 * DES-CBC-MAC (FIPS 113) avec ZAK simple longueur, dechiffree depuis key_under_kek.
 */
@Service
public class SwamMac {

    private static final Logger log = LoggerFactory.getLogger(SwamMac.class);
    private static final String MGID = "TESTGRP01";
    private static final int MAC_FIELD = 128;

    private final JposHsmService hsm;
    private final SwamAcqKeyRepository acqKeyRepo;
    private final SwamKekRepository kekRepo;

    @Value("${swam.mac.fields:4,11,37,41,42}")   private String macFields;
    @Value("${swam.mac.representation:ascii}")   private String macRepr;

    public SwamMac(JposHsmService hsm, SwamAcqKeyRepository acqKeyRepo, SwamKekRepository kekRepo) {
        this.hsm = hsm; this.acqKeyRepo = acqKeyRepo; this.kekRepo = kekRepo;
    }

    /** Calcule et pose le DE128. Renvoie le MAC hex (ou null si MAK/KEK absente). */
    public String apply(ISOMsg m) throws Exception {
        SwamAcqKey mak = acqKeyRepo.findByMemberGroupIdAndKeyTypeAndStatus(MGID, "MAK", "ACTIVE").orElse(null);
        if (mak == null) { log.warn("[SWAM-MAC] MAK absente -> pas de DE128"); return null; }
        SwamKek kek = kekRepo.findByMemberGroupId(MGID).orElse(null);
        if (kek == null || kek.getKekClear() == null) { log.warn("[SWAM-MAC] KEK claire absente"); return null; }

        byte[] input = McMacBuilder.build(m, macFields, macRepr);
        byte[] mac = hsm.generateMacSingle(input, mak.getKeyUnderKek(), kek.getKekClear());
        m.set(MAC_FIELD, mac);
        String hex = ISOUtil.hexString(mac);
        log.info("[SWAM-MAC] DE128 pose (fields={} repr={}) mac={}", macFields, macRepr, hex);
        return hex;
    }
}
