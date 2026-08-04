package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.HsmService;
import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.iso.ISOUtil;
import org.jpos.security.SecureDESKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    private final String way4KeyId;
    private final String way4TakBlock;
    private final String way4TpkBlock;

    public WayPosLocalRkiBootstrapService(
            JposHsmService hsm,
            WayPosWorkingKeyBootstrapService bootstrap,
            WayPosKeyExchangeService exchange,
            @Value("${WAY_POS_TAK_HEX:}") String initialTakHex,
            @Value("${WAY_POS_TAMK_HEX:}") String tamkHex,
            @Value("${WAY_POS_TPMK_HEX:}") String tpmkHex,
            @Value("${WAY_POS_TAMK_ID:00}") String tamkId,
            @Value("${WAY_POS_TPMK_ID:00}") String tpmkId,
            @Value("${way-pos.rki-way4-key-id:27}") String way4KeyId,
            @Value("${way-pos.rki-way4-tak-block-ascii:}") String way4TakBlock,
            @Value("${way-pos.rki-way4-tpk-block-ascii:}") String way4TpkBlock) {
        this.hsm = hsm;
        this.bootstrap = bootstrap;
        this.exchange = exchange;
        this.initialTakHex = initialTakHex;
        this.tamkHex = tamkHex;
        this.tpmkHex = tpmkHex;
        this.tamkId = tamkId;
        this.tpmkId = tpmkId;
        this.way4KeyId = way4KeyId;
        this.way4TakBlock = way4TakBlock;
        this.way4TpkBlock = way4TpkBlock;
    }

    @Transactional
    public Map<String, Object> bootstrap(String terminalId) throws Exception {
        requireKey(initialTakHex, "WAY_POS_TAK_HEX", 16);
        requireKey(tamkHex, "WAY_POS_TAMK_HEX", 24);
        requireKey(tpmkHex, "WAY_POS_TPMK_HEX", 24);
        boolean hasWay4Tak = way4TakBlock != null && !way4TakBlock.isBlank();
        boolean hasWay4Tpk = way4TpkBlock != null && !way4TpkBlock.isBlank();
        if (hasWay4Tak != hasWay4Tpk) {
            throw new IllegalArgumentException(
                    "Both Way4 TAK and TPK blocks are required together");
        }

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
                tamkId, "TAMK", tamkHex, way4TakBlock);
        provision(terminalId, "TPK", nextKeyId("TPK"),
                tpmkId, "TPMK", tpmkHex, way4TpkBlock);
        boolean way4WireFormat = hasWay4Tak && hasWay4Tpk;
        return Map.of(
                "terminalId", terminalId,
                "initialTak", "ACTIVE",
                "rkiTak", "PENDING_OR_PRESENT",
                "rkiTpk", "PENDING_OR_PRESENT",
                "wireFormat", way4WireFormat
                        ? "WAY4_F20_DF40_2" : "LOCAL_DF40_1",
                "keyId", way4WireFormat ? way4KeyId : "GENERATED",
                "de48Length", way4WireFormat ? 292 : 0);
    }

    private void provision(
            String terminalId, String keyType, String keyId,
            String masterId, String masterType, String masterHex,
            String observedWay4Block)
            throws Exception {
        HsmService.KeyResult generated = hsm.generateWorkingKey(
                keyType, WORKING_KEY_LENGTH, masterHex);
        byte[] transportBlock = generated.keyUnderKek;
        String effectiveKeyId = keyId;
        if (observedWay4Block != null && !observedWay4Block.isBlank()) {
            transportBlock = requireWay4Block(
                    observedWay4Block, keyType, way4KeyId);
            effectiveKeyId = way4KeyId;
        }
        exchange.provision(new WayPosKeyExchangeService.ProvisionedKey(
                terminalId, keyType, effectiveKeyId, "T", generated.kcv,
                masterId, masterType, transportBlock,
                generated.keyUnderLmkHex, WORKING_KEY_LENGTH,
                "0", null));
    }

    private static byte[] requireWay4Block(
            String value, String keyType, String keyId) {
        if (keyId == null || !keyId.matches("[0-9]{2}")) {
            throw new IllegalArgumentException(
                    "WAY_POS_RKI_WAY4_KEY_ID must contain two digits");
        }
        byte[] block = value.getBytes(StandardCharsets.US_ASCII);
        String expectedUsage = "TPK".equals(keyType) ? "P0" : "M3";
        if (block.length != 112 || !value.startsWith("D0112" + expectedUsage)
                || !keyId.equals(value.substring(9, 11))) {
            throw new IllegalArgumentException(
                    keyType + " Way4 block must be D0112/" + expectedUsage
                            + " with key ID " + keyId);
        }
        return block;
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
