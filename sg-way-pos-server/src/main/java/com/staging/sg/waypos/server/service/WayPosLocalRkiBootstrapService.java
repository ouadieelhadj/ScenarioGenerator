package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.iso.ISOUtil;
import org.jpos.security.SecureDESKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Local connected-test bootstrap. Never enabled outside connected-e2e. */
@Service
@Profile("connected-e2e")
public class WayPosLocalRkiBootstrapService {
    private static final int WORKING_KEY_LENGTH = 16;
    private final JposHsmService hsm;
    private final WayPosWorkingKeyBootstrapService bootstrap;
    private final WayPosKeyExchangeService exchange;
    private final String initialTakHex;
    private final String tamkHex;
    private final String tpmkHex;
    private final String tamkId;
    private final String tpmkId;

    public WayPosLocalRkiBootstrapService(
            JposHsmService hsm,
            WayPosWorkingKeyBootstrapService bootstrap,
            WayPosKeyExchangeService exchange,
            @Value("${WAY_POS_TAK_HEX:}") String initialTakHex,
            @Value("${WAY_POS_TAMK_HEX:}") String tamkHex,
            @Value("${WAY_POS_TPMK_HEX:}") String tpmkHex,
            @Value("${WAY_POS_TAMK_ID:00}") String tamkId,
            @Value("${WAY_POS_TPMK_ID:00}") String tpmkId) {
        this.hsm = hsm;
        this.bootstrap = bootstrap;
        this.exchange = exchange;
        this.initialTakHex = initialTakHex;
        this.tamkHex = tamkHex;
        this.tpmkHex = tpmkHex;
        this.tamkId = tamkId;
        this.tpmkId = tpmkId;
    }

    @Transactional
    public Map<String, Object> bootstrap(String terminalId) throws Exception {
        requireKey(initialTakHex, "WAY_POS_TAK_HEX", 16);
        requireKey(tamkHex, "WAY_POS_TAMK_HEX", 24);
        requireKey(tpmkHex, "WAY_POS_TPMK_HEX", 24);

        byte[] initialTak = ISOUtil.hex2byte(initialTakHex);
        try {
            SecureDESKey underLmk = hsm.formClearKey("TAK", initialTakHex);
            String kcv = hsm.computeKcv(initialTak);
            bootstrap.activate(terminalId, "TAK",
                    ISOUtil.hexString(underLmk.getKeyBytes()), kcv,
                    WORKING_KEY_LENGTH);
        } finally {
            java.util.Arrays.fill(initialTak, (byte) 0);
        }

        provision(terminalId, "TAK", nextKeyId("TAK"),
                tamkId, "TAMK", tamkHex);
        provision(terminalId, "TPK", nextKeyId("TPK"),
                tpmkId, "TPMK", tpmkHex);
        return Map.of(
                "terminalId", terminalId,
                "initialTak", "ACTIVE",
                "rkiTak", "PENDING_OR_PRESENT",
                "rkiTpk", "PENDING_OR_PRESENT");
    }

    private void provision(
            String terminalId, String keyType, String keyId,
            String masterId, String masterType, String masterHex)
            throws Exception {
        HsmService.KeyResult generated = hsm.generateWorkingKey(
                keyType, WORKING_KEY_LENGTH, masterHex);
        exchange.provision(new WayPosKeyExchangeService.ProvisionedKey(
                terminalId, keyType, keyId, "T", generated.kcv,
                masterId, masterType, generated.keyUnderKek,
                generated.keyUnderLmkHex, WORKING_KEY_LENGTH,
                "0", null));
    }

    private static String nextKeyId(String keyType) {
        return "RKI-" + keyType + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static void requireKey(
            String value, String name, int expectedBytes) {
        if (value == null
                || !value.matches("(?i)[0-9a-f]{" + (expectedBytes * 2) + "}")) {
            throw new IllegalArgumentException(
                    name + " must contain " + expectedBytes + " test-key bytes");
        }
    }
}
