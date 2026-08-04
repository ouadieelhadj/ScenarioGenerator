package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(
            WayPosInitialKeyChangeAuthenticator.class);

    private final JposHsmService hsm;
    private final boolean enabled;
    private final boolean logKcvEnabled;
    private final boolean allowKcvMismatchTestOnly;
    private final String tamkHex;
    private final String tpmkHex;
    private final String tamkId;
    private final String tpmkId;

    public WayPosInitialKeyChangeAuthenticator(
            JposHsmService hsm,
            @Value("${way-pos.local-test-bootstrap-enabled:false}") boolean enabled,
            @Value("${way-pos.rki-log-kcv-enabled:false}") boolean logKcvEnabled,
            @Value("${way-pos.rki-allow-kcv-mismatch-test-only:false}")
                    boolean allowKcvMismatchTestOnly,
            @Value("${WAY_POS_TAMK_HEX:}") String tamkHex,
            @Value("${WAY_POS_TPMK_HEX:}") String tpmkHex,
            @Value("${WAY_POS_TAMK_ID:00}") String tamkId,
            @Value("${WAY_POS_TPMK_ID:00}") String tpmkId) {
        this.hsm = hsm;
        this.enabled = enabled;
        this.logKcvEnabled = logKcvEnabled;
        this.allowKcvMismatchTestOnly = allowKcvMismatchTestOnly;
        this.tamkHex = tamkHex;
        this.tpmkHex = tpmkHex;
        this.tamkId = tamkId;
        this.tpmkId = tpmkId;
    }

    public boolean authenticates(ISOMsg request) {
        if (!isInitialKeyChange(request)) {
            return false;
        }
        if (!enabled) {
            log.warn("[WAY-POS][RKI] initial request rejected: local bootstrap disabled");
            return false;
        }
        if (!validTripleLengthKey(tamkHex) || !validTripleLengthKey(tpmkHex)) {
            log.warn("[WAY-POS][RKI] initial request rejected: master-key configuration missing or invalid");
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
            boolean tamkMatches = matches(statuses, "TAMK", tamkId, tamkHex);
            boolean tpmkMatches = matches(statuses, "TPMK", tpmkId, tpmkHex);
            if (logKcvEnabled) {
                log.info("[WAY-POS][RKI][TEST-ONLY] initial key evidence: "
                                + "TAMK={} TPMK={} TAMK_KCV={} TPMK_KCV={}",
                        tamkMatches ? "MATCH" : "MISMATCH",
                        tpmkMatches ? "MATCH" : "MISMATCH",
                        receivedKcv(statuses, "TAMK", tamkId),
                        receivedKcv(statuses, "TPMK", tpmkId));
            } else {
                log.info("[WAY-POS][RKI] initial key evidence: TAMK={} TPMK={} "
                                + "TAMK_KCV_FP={} TPMK_KCV_FP={}",
                        tamkMatches ? "MATCH" : "MISMATCH",
                        tpmkMatches ? "MATCH" : "MISMATCH",
                        kcvFingerprint(statuses, "TAMK", tamkId),
                        kcvFingerprint(statuses, "TPMK", tpmkId));
            }
            boolean matches = tamkMatches && tpmkMatches;
            if (!matches && allowKcvMismatchTestOnly
                    && hasCompleteKcvEvidence(statuses, "TAMK", tamkId)
                    && hasCompleteKcvEvidence(statuses, "TPMK", tpmkId)) {
                log.warn("[WAY-POS][RKI][TEST-ONLY] KCV mismatch explicitly bypassed; "
                        + "the 930000 confirmation MAC is required as final key proof");
                return true;
            }
            return matches;
        } catch (Exception e) {
            log.warn("[WAY-POS][RKI] initial request rejected: key evidence cannot be verified ({})",
                    e.getClass().getSimpleName());
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

    private static String kcvFingerprint(
            List<WayPosKeyExchangeCodec.KeyStatusDetails> statuses,
            String type, String id) throws Exception {
        String kcv = statuses.stream()
                .filter(value -> type.equalsIgnoreCase(value.keyType())
                        && id.equals(value.keyId()))
                .map(WayPosKeyExchangeCodec.KeyStatusDetails::kcv)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (kcv == null) return "ABSENT";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                kcv.toUpperCase().getBytes(StandardCharsets.US_ASCII));
        try {
            return ISOUtil.hexString(digest).substring(0, 16).toUpperCase();
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    static String receivedKcv(
            List<WayPosKeyExchangeCodec.KeyStatusDetails> statuses,
            String type, String id) {
        String kcv = statuses.stream()
                .filter(value -> type.equalsIgnoreCase(value.keyType())
                        && id.equals(value.keyId()))
                .map(WayPosKeyExchangeCodec.KeyStatusDetails::kcv)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (kcv == null) return "ABSENT";
        String normalized = kcv.toUpperCase();
        return normalized.matches("[0-9A-F]{6}") ? normalized : "INVALID";
    }

    private static boolean hasCompleteKcvEvidence(
            List<WayPosKeyExchangeCodec.KeyStatusDetails> statuses,
            String type, String id) {
        return statuses.stream().anyMatch(value ->
                type.equalsIgnoreCase(value.keyType())
                        && id.equals(value.keyId())
                        && "0".equals(value.status())
                        && value.kcv() != null
                        && value.kcv().matches("(?i)[0-9a-f]{6}"));
    }

    private static boolean validTripleLengthKey(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{48}");
    }
}
