package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.waypos.server.domain.PosTerminalKey;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalKeyRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class WayPosKeyExchangeService {
    private static final int ISO_PRIVATE_FIELD_CAPACITY = 999;
    private static final int WORKING_KEY_LENGTH = 16;
    private final PosTerminalKeyRepository keys;
    private final PosTerminalProfileRepository terminals;
    private final JposHsmService hsm;
    private final boolean generateTr31;
    private final String tamkHex;
    private final String tpmkHex;
    private final String tamkId;
    private final String tpmkId;

    public WayPosKeyExchangeService(
            PosTerminalKeyRepository keys,
            PosTerminalProfileRepository terminals,
            JposHsmService hsm,
            @Value("${way-pos.rki-generate-tr31-enabled:false}")
                    boolean generateTr31,
            @Value("${WAY_POS_TAMK_HEX:}") String tamkHex,
            @Value("${WAY_POS_TPMK_HEX:}") String tpmkHex,
            @Value("${WAY_POS_TAMK_ID:00}") String tamkId,
            @Value("${WAY_POS_TPMK_ID:00}") String tpmkId) {
        this.keys = keys;
        this.terminals = terminals;
        this.hsm = hsm;
        this.generateTr31 = generateTr31;
        this.tamkHex = tamkHex;
        this.tpmkHex = tpmkHex;
        this.tamkId = tamkId;
        this.tpmkId = tpmkId;
    }

    @Transactional
    public List<byte[]> exchange(ISOMsg request, PosTerminalProfile terminal) {
        applyTerminalStatuses(request, terminal);
        List<PosTerminalKey> generated = generateTr31
                ? generateWay4F20Pair(terminalId(request)) : List.of();
        List<PosTerminalKey> outgoing = generated.isEmpty()
                ? keys.findTop15ByTerminalIdAndKeyStatusInOrderByIdDesc(
                        terminalId(request), List.of("PENDING", "DELIVERED"))
                : generated;
        if (outgoing.isEmpty()) return List.of();

        List<PosTerminalKey> selected = latestTakAndTpk(outgoing);
        List<WayPosKeyExchangeCodec.KeyBlock> wireBlocks = selected.stream()
                .map(PosTerminalKey::toWireBlock)
                .toList();
        List<byte[]> fields = isWay4F20Pair(wireBlocks)
                ? List.of(WayPosKeyExchangeCodec.encodeWay4F20Response(wireBlocks))
                : WayPosKeyExchangeCodec.encodeResponseFields(
                        wireBlocks, ISO_PRIVATE_FIELD_CAPACITY);
        selected.stream()
                .filter(key -> "PENDING".equals(key.getKeyStatus()))
                .forEach(PosTerminalKey::markDelivered);
        keys.saveAll(selected);
        return fields;
    }

    private List<PosTerminalKey> generateWay4F20Pair(String terminalId) {
        requireAesKbpk(tamkHex, "WAY_POS_TAMK_HEX");
        requireAesKbpk(tpmkHex, "WAY_POS_TPMK_HEX");
        String keyId = nextNumericKeyId(terminalId);
        try {
            JposHsmService.Tr31KeyResult tak = hsm.generateTr31WorkingKey(
                    "TAK", WORKING_KEY_LENGTH, tamkHex, keyId);
            JposHsmService.Tr31KeyResult tpk = hsm.generateTr31WorkingKey(
                    "TPK", WORKING_KEY_LENGTH, tpmkHex, keyId);
            PosTerminalKey takEntity = generatedKey(
                    terminalId, "TAK", keyId, tamkId, "TAMK", tak);
            PosTerminalKey tpkEntity = generatedKey(
                    terminalId, "TPK", keyId, tpmkId, "TPMK", tpk);
            keys.saveAll(List.of(takEntity, tpkEntity));
            return List.of(tpkEntity, takEntity);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to generate Way4/F20 TR-31 working keys", e);
        }
    }

    private static PosTerminalKey generatedKey(
            String terminalId, String keyType, String keyId,
            String masterId, String masterType,
            JposHsmService.Tr31KeyResult result) {
        if (result.keyBlockAscii().length != 112) {
            throw new IllegalStateException(
                    keyType + " TR-31 block must contain 112 ASCII bytes");
        }
        return PosTerminalKey.pending(
                terminalId, keyType, keyId, "T", result.kcv(),
                masterId, masterType, result.keyBlockAscii(),
                result.keyUnderLmkHex(), result.keyLength(),
                "0", null);
    }

    private String nextNumericKeyId(String terminalId) {
        boolean[] used = new boolean[100];
        for (PosTerminalKey key
                : keys.findByTerminalIdOrderByIdDesc(terminalId)) {
            if (key.getKeyId() != null
                    && key.getKeyId().matches("[0-9]{2}")) {
                used[Integer.parseInt(key.getKeyId())] = true;
            }
        }
        for (int candidate = 27; candidate < 100; candidate++) {
            if (!used[candidate]) return String.format("%02d", candidate);
        }
        for (int candidate = 0; candidate < 27; candidate++) {
            if (!used[candidate]) return String.format("%02d", candidate);
        }
        throw new IllegalStateException("No free two-digit RKI key ID");
    }

    private static void requireAesKbpk(String value, String name) {
        if (value == null || !value.matches(
                "(?i)([0-9a-f]{32}|[0-9a-f]{48}|[0-9a-f]{64})")) {
            throw new IllegalStateException(
                    name + " must contain an AES-128/192/256 test KBPK");
        }
    }

    private static List<PosTerminalKey> latestTakAndTpk(
            List<PosTerminalKey> newestFirst) {
        PosTerminalKey tak = null;
        PosTerminalKey tpk = null;
        for (PosTerminalKey key : newestFirst) {
            if (tak == null && "TAK".equals(key.getKeyType())) tak = key;
            if (tpk == null && "TPK".equals(key.getKeyType())) tpk = key;
            if (tak != null && tpk != null) break;
        }
        if (tak == null || tpk == null) {
            throw new IllegalStateException(
                    "RKI distribution requires one TAK and one TPK");
        }
        return List.of(tpk, tak);
    }

    private static boolean isWay4F20Pair(
            List<WayPosKeyExchangeCodec.KeyBlock> blocks) {
        return blocks.size() == 2
                && blocks.stream().allMatch(
                        key -> "2".equals(key.keyBlockFormat()));
    }

    @Transactional
    public void confirm(ISOMsg request, PosTerminalProfile terminal) {
        applyTerminalStatuses(request, terminal);
    }

    @Transactional
    public void provision(ProvisionedKey value) {
        if (keys.findByTerminalIdAndKeyTypeAndKeyId(
                value.terminalId(), value.keyType(), value.keyId()).isPresent()) {
            throw new IllegalArgumentException("This terminal key ID already exists");
        }
        PosTerminalProfile terminal = terminals.findById(value.terminalId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal"));
        if (!terminal.isEnabled()) {
            throw new IllegalArgumentException("Terminal is disabled");
        }
        validateProvisioning(value);
        keys.save(PosTerminalKey.pending(
                value.terminalId(), value.keyType(), value.keyId(),
                value.algorithm(), value.kcv(), value.masterKeyId(),
                value.masterKeyType(), value.ansiX917Block(),
                value.keyUnderLmk(), value.keyLength(),
                value.actionCode(), value.replacementKeyId()));
    }

    public List<PosTerminalKey> candidateAuthenticationKeys(String terminalId) {
        return keys.findByTerminalIdAndKeyTypeAndKeyStatusIn(
                terminalId, "TAK", List.of("DELIVERED"));
    }

    private void applyTerminalStatuses(
            ISOMsg request, PosTerminalProfile terminal) {
        List<WayPosKeyExchangeCodec.KeyStatus> statuses = new ArrayList<>();
        if (request.hasField(48)) {
            statuses.addAll(WayPosKeyExchangeCodec.decodeStatuses(
                    request.getBytes(48)));
        }
        if (request.hasField(59)) {
            statuses.addAll(WayPosKeyExchangeCodec.decodeStatuses(
                    request.getBytes(59)));
        }
        for (WayPosKeyExchangeCodec.KeyStatus status : statuses) {
            PosTerminalKey key = keys.findByTerminalIdAndKeyTypeAndKeyId(
                            terminalId(request), status.keyType(), status.keyId())
                    .orElse(null);
            if (key == null) continue;
            key.acknowledge(status.status());
            if ("0".equals(status.status())
                    && ("TAK".equals(key.getKeyType())
                    || "TPK".equals(key.getKeyType()))) {
                terminal.activateWorkingKey(
                        key.getKeyType(), key.getKeyUnderLmk(), key.getKcv(),
                        key.getKeyLength());
            }
            keys.save(key);
        }
        terminals.save(terminal);
    }

    private static void validateProvisioning(ProvisionedKey value) {
        if (value.terminalId() == null || value.terminalId().isBlank()
                || value.keyType() == null || value.keyType().isBlank()
                || value.keyId() == null || value.keyId().isBlank()
                || value.masterKeyId() == null || value.masterKeyId().isBlank()
                || value.masterKeyType() == null
                || value.masterKeyType().isBlank()) {
            throw new IllegalArgumentException(
                    "Terminal, key and master-key references are mandatory");
        }
        String action = value.actionCode() == null
                ? "0" : value.actionCode();
        if (("0".equals(action) || "1".equals(action))
                && (value.ansiX917Block() == null
                || value.ansiX917Block().length == 0)) {
            throw new IllegalArgumentException(
                    "A real ANSI X9.17 block is mandatory");
        }
        if (("TAK".equals(value.keyType()) || "TPK".equals(value.keyType()))
                && "0".equals(action)
                && (value.keyUnderLmk() == null || value.kcv() == null
                || value.keyLength() == null)) {
            throw new IllegalArgumentException(
                    "The server-side key under LMK, KCV and length are mandatory");
        }
    }

    private static String terminalId(ISOMsg request) {
        return request.getString(41);
    }

    public record ProvisionedKey(
            String terminalId,
            String keyType,
            String keyId,
            String algorithm,
            String kcv,
            String masterKeyId,
            String masterKeyType,
            byte[] ansiX917Block,
            String keyUnderLmk,
            Integer keyLength,
            String actionCode,
            String replacementKeyId) {
        public ProvisionedKey {
            ansiX917Block = ansiX917Block == null
                    ? null : ansiX917Block.clone();
        }

        @Override
        public byte[] ansiX917Block() {
            return ansiX917Block == null ? null : ansiX917Block.clone();
        }
    }
}
