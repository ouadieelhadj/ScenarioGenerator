package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Security-officer bootstrap for the first terminal TAK/TPK. Only a key
 * already encrypted under the WayPos LMK is accepted.
 */
@Service
public class WayPosWorkingKeyBootstrapService {
    private final PosTerminalProfileRepository terminals;
    private final JposHsmService hsm;

    public WayPosWorkingKeyBootstrapService(
            PosTerminalProfileRepository terminals, JposHsmService hsm) {
        this.terminals = terminals;
        this.hsm = hsm;
    }

    @Transactional
    public PosTerminalProfile activate(
            String terminalId, String keyType, String keyUnderLmk,
            String kcv, Integer keyLength) {
        String normalizedType = keyType == null
                ? null : keyType.toUpperCase(Locale.ROOT);
        validateMetadata(
                terminalId, normalizedType, keyUnderLmk, kcv, keyLength);

        PosTerminalProfile terminal = terminals
                .findLockedByTerminalId(terminalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown terminal"));
        if (!terminal.isEnabled()) {
            throw new IllegalArgumentException("Terminal is disabled");
        }

        try {
            if (!hsm.validateKeyUnderLmk(
                    normalizedType, keyUnderLmk, kcv, keyLength)) {
                throw new IllegalArgumentException(
                        "Working-key KCV does not match the key under LMK");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Working key cannot be validated by the configured HSM", e);
        }

        terminal.activateWorkingKey(
                normalizedType, keyUnderLmk, kcv, keyLength);
        return terminals.save(terminal);
    }

    private static void validateMetadata(
            String terminalId, String keyType, String keyUnderLmk,
            String kcv, Integer keyLength) {
        if (terminalId == null || !terminalId.matches("[A-Za-z0-9]{8}")) {
            throw new IllegalArgumentException("Invalid terminal ID");
        }
        if (!("TAK".equals(keyType) || "TPK".equals(keyType))) {
            throw new IllegalArgumentException("Only TAK or TPK can be bootstrapped");
        }
        if (keyUnderLmk == null
                || !keyUnderLmk.matches("(?i)[0-9a-f]+")
                || (keyUnderLmk.length() & 1) != 0) {
            throw new IllegalArgumentException(
                    "A hexadecimal key encrypted under the WayPos LMK is mandatory");
        }
        if (kcv == null || !kcv.matches("(?i)[0-9a-f]{6}")) {
            throw new IllegalArgumentException("A six-hex-digit KCV is mandatory");
        }
        if (keyLength == null || !(keyLength == 8 || keyLength == 16)) {
            throw new IllegalArgumentException(
                    "Working-key length must be 8 or 16 bytes");
        }
    }
}
