package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fail-closed authentication for the initial, MAC-less OpenWay RKI request.
 * It is available only when the explicit connected-test bootstrap switch is
 * enabled and validates both terminal master-key KCVs from DE48/DE59.
 */
@Service
public class WayPosInitialKeyChangeAuthenticator {
    private final JposHsmService hsm;
    private final boolean enabled;
    private final String tamkHex;
    private final String tpmkHex;
    private final String tamkId;
    private final String tpmkId;

    public WayPosInitialKeyChangeAuthenticator(
            JposHsmService hsm,
            @Value("${way-pos.local-test-bootstrap-enabled:false}") boolean enabled,
            @Value("${WAY_POS_TAMK_HEX:}") String tamkHex,
            @Value("${WAY_POS_TPMK_HEX:}") String tpmkHex,
            @Value("${WAY_POS_TAMK_ID:00}") String tamkId,
            @Value("${WAY_POS_TPMK_ID:00}") String tpmkId) {
        this.hsm = hsm;
        this.enabled = enabled;
        this.tamkHex = tamkHex;
        this.tpmkHex = tpmkHex;
        this.tamkId = tamkId;
        this.tpmkId = tpmkId;
    }

    public boolean authenticates(ISOMsg request) {
        if (!enabled || !isInitialKeyChange(request)
                || !validTripleLengthKey(tamkHex)
                || !validTripleLengthKey(tpmkHex)) {
            return false;
        }
        try {
            List<WayPosKeyExchangeCodec.KeyStatusDetails> statuses = new ArrayList<>();
            if (request.hasField(48)) {
                statuses.addAll(WayPosKeyExchangeCodec.decodeStatusDetails(
                        request.getBytes(48)));
            }
            if (request.hasField(59)) {
                statuses.addAll(WayPosKeyExchangeCodec.decodeStatusDetails(
                        request.getBytes(59)));
            }
            return matches(statuses, "TAMK", tamkId, tamkHex)
                    && matches(statuses, "TPMK", tpmkId, tpmkHex);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matches(
            List<WayPosKeyExchangeCodec.KeyStatusDetails> statuses,
            String type, String id, String clearHex) throws Exception {
        WayPosKeyExchangeCodec.KeyStatusDetails status = statuses.stream()
                .filter(value -> type.equalsIgnoreCase(value.keyType())
                        && id.equals(value.keyId())
                        && "0".equals(value.status()))
                .findFirst()
                .orElse(null);
        if (status == null || status.kcv() == null) return false;
        byte[] clear = ISOUtil.hex2byte(clearHex);
        try {
            String expected = hsm.computeKcv(clear);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    status.kcv().toUpperCase().getBytes(StandardCharsets.US_ASCII));
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    private static boolean isInitialKeyChange(ISOMsg request) {
        try {
            return "0800".equals(request.getMTI())
                    && "960000".equals(request.getString(3))
                    && !request.hasField(64);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validTripleLengthKey(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{48}");
    }
}
