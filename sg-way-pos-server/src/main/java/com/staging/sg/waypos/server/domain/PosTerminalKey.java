package com.staging.sg.waypos.server.domain;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "pos_terminal_keys", uniqueConstraints = @UniqueConstraint(
        name = "uk_pos_terminal_key", columnNames = {"terminal_id", "key_type", "key_id"}))
public class PosTerminalKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;
    @Column(name = "key_type", nullable = false, length = 8)
    private String keyType;
    @Column(name = "key_id", nullable = false, length = 32)
    private String keyId;
    @Column(name = "key_status", nullable = false, length = 16)
    private String keyStatus;
    @Column(name = "key_algorithm", length = 1)
    private String algorithm;
    @Column(name = "kcv", length = 6)
    private String kcv;
    @Column(name = "master_key_id", nullable = false, length = 32)
    private String masterKeyId;
    @Column(name = "master_key_type", nullable = false, length = 8)
    private String masterKeyType;
    @Column(name = "ansi_x917_block", columnDefinition = "bytea")
    private byte[] ansiX917Block;
    @Column(name = "key_under_lmk")
    private String keyUnderLmk;
    @Column(name = "key_length")
    private Integer keyLength;
    @Column(name = "action_code", nullable = false, length = 1)
    private String actionCode;
    @Column(name = "replacement_key_id", length = 32)
    private String replacementKeyId;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected PosTerminalKey() {}

    public static PosTerminalKey pending(
            String terminalId, String keyType, String keyId, String algorithm,
            String kcv, String masterKeyId, String masterKeyType,
            byte[] ansiX917Block, String keyUnderLmk, Integer keyLength,
            String actionCode, String replacementKeyId) {
        PosTerminalKey value = new PosTerminalKey();
        value.terminalId = terminalId;
        value.keyType = keyType;
        value.keyId = keyId;
        value.keyStatus = "PENDING";
        value.algorithm = algorithm;
        value.kcv = kcv;
        value.masterKeyId = masterKeyId;
        value.masterKeyType = masterKeyType;
        value.ansiX917Block = ansiX917Block == null ? null : ansiX917Block.clone();
        value.keyUnderLmk = keyUnderLmk;
        value.keyLength = keyLength;
        value.actionCode = actionCode == null ? "0" : actionCode;
        value.replacementKeyId = replacementKeyId;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public String getTerminalId() { return terminalId; }
    public String getKeyType() { return keyType; }
    public String getKeyId() { return keyId; }
    public String getKeyStatus() { return keyStatus; }
    public String getKcv() { return kcv; }
    public String getKeyUnderLmk() { return keyUnderLmk; }
    public Integer getKeyLength() { return keyLength; }

    public WayPosKeyExchangeCodec.KeyBlock toWireBlock() {
        String keyBlockFormat = isObservedWay4F20Block() ? "2" : "1";
        return new WayPosKeyExchangeCodec.KeyBlock(
                keyId, keyType, kcv, algorithm, masterKeyId, masterKeyType,
                ansiX917Block, keyBlockFormat, actionCode, "0",
                replacementKeyId);
    }

    private boolean isObservedWay4F20Block() {
        return ansiX917Block != null
                && ansiX917Block.length == 112
                && ansiX917Block[0] == 'D'
                && ansiX917Block[1] == '0'
                && ansiX917Block[2] == '1'
                && ansiX917Block[3] == '1'
                && ansiX917Block[4] == '2';
    }

    public void markDelivered() {
        keyStatus = "DELIVERED";
        deliveredAt = Instant.now();
        updatedAt = deliveredAt;
    }

    public void acknowledge(String terminalStatus) {
        keyStatus = switch (terminalStatus) {
            case "0" -> "ACTIVE";
            case "1" -> "HARDWARE_ERROR";
            case "2" -> "INVALID_BLOCK";
            default -> "NOT_READY";
        };
        acknowledgedAt = Instant.now();
        updatedAt = acknowledgedAt;
    }
}
