package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.waypos.server.domain.PosTerminalKey;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalKeyRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class WayPosKeyExchangeService {
    private static final int ISO_PRIVATE_FIELD_CAPACITY = 999;
    private final PosTerminalKeyRepository keys;
    private final PosTerminalProfileRepository terminals;

    public WayPosKeyExchangeService(
            PosTerminalKeyRepository keys, PosTerminalProfileRepository terminals) {
        this.keys = keys;
        this.terminals = terminals;
    }

    @Transactional
    public List<byte[]> exchange(ISOMsg request, PosTerminalProfile terminal) {
        applyTerminalStatuses(request, terminal);
        List<PosTerminalKey> outgoing =
                keys.findTop15ByTerminalIdAndKeyStatusInOrderByIdAsc(
                        terminalId(request), List.of("PENDING", "DELIVERED"));
        if (outgoing.isEmpty()) return List.of();

        List<byte[]> fields = WayPosKeyExchangeCodec.encodeResponseFields(
                outgoing.stream().map(PosTerminalKey::toWireBlock).toList(),
                ISO_PRIVATE_FIELD_CAPACITY);
        outgoing.stream()
                .filter(key -> "PENDING".equals(key.getKeyStatus()))
                .forEach(PosTerminalKey::markDelivered);
        keys.saveAll(outgoing);
        return fields;
    }

    @Transactional
    public void provision(ProvisionedKey value) {
        if (keys.findByTerminalIdAndKeyTypeAndKeyId(
                value.terminalId(), value.keyType(), value.keyId()).isPresent()) {
            throw new IllegalArgumentException("This terminal key ID already exists");
        }
        PosTerminalProfile terminal = terminals.findById(value.terminalId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal"));
        if (!terminal.isEnabled()) throw new IllegalArgumentException("Terminal is disabled");
        validateProvisioning(value);
        keys.save(PosTerminalKey.pending(
                value.terminalId(), value.keyType(), value.keyId(), value.algorithm(),
                value.kcv(), value.masterKeyId(), value.masterKeyType(),
                value.ansiX917Block(), value.keyUnderLmk(), value.keyLength(),
                value.actionCode(), value.replacementKeyId()));
    }

    public List<PosTerminalKey> candidateAuthenticationKeys(String terminalId) {
        return keys.findByTerminalIdAndKeyTypeAndKeyStatusIn(
                terminalId, "TAK", List.of("DELIVERED"));
    }

    private void applyTerminalStatuses(ISOMsg request, PosTerminalProfile terminal) {
        List<WayPosKeyExchangeCodec.KeyStatus> statuses = new ArrayList<>();
        if (request.hasField(48)) {
            statuses.addAll(WayPosKeyExchangeCodec.decodeStatuses(request.getBytes(48)));
        }
        if (request.hasField(59)) {
            statuses.addAll(WayPosKeyExchangeCodec.decodeStatuses(request.getBytes(59)));
        }
        for (WayPosKeyExchangeCodec.KeyStatus status : statuses) {
            PosTerminalKey key = keys.findByTerminalIdAndKeyTypeAndKeyId(
                            terminalId(request), status.keyType(), status.keyId())
                    .orElse(null);
            if (key == null) continue;
            key.acknowledge(status.status());
            if ("0".equals(status.status())
                    && ("TAK".equals(key.getKeyType()) || "TPK".equals(key.getKeyType()))) {
                terminal.activateWorkingKey(
                        key.getKeyType(), key.getKeyUnderLmk(), key.getKcv(), key.getKeyLength());
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
                || value.masterKeyType() == null || value.masterKeyType().isBlank()) {
            throw new IllegalArgumentException("Terminal, key and master-key references are mandatory");
        }
        String action = value.actionCode() == null ? "0" : value.actionCode();
        if (("0".equals(action) || "1".equals(action))
                && (value.ansiX917Block() == null || value.ansiX917Block().length == 0)) {
            throw new IllegalArgumentException("A real ANSI X9.17 block is mandatory");
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
            ansiX917Block = ansiX917Block == null ? null : ansiX917Block.clone();
        }
        @Override public byte[] ansiX917Block() {
            return ansiX917Block == null ? null : ansiX917Block.clone();
        }
    }
}
