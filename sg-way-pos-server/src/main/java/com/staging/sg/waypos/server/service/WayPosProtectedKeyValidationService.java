package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import org.springframework.stereotype.Service;

/**
 * Validates metadata for any key already protected by the WayPos LMK.
 * Clear-key import is deliberately outside this API.
 */
@Service
public class WayPosProtectedKeyValidationService {
    private final JposHsmService hsm;

    public WayPosProtectedKeyValidationService(JposHsmService hsm) {
        this.hsm = hsm;
    }

    public void requireValid(
            String keyType, String keyUnderLmk, String kcv, int keyLength) {
        if (keyType == null || keyType.isBlank()
                || keyUnderLmk == null
                || !keyUnderLmk.matches("(?i)[0-9a-f]+")
                || (keyUnderLmk.length() & 1) != 0
                || kcv == null || !kcv.matches("(?i)[0-9a-f]{6}")
                || !(keyLength == 8 || keyLength == 16 || keyLength == 24)) {
            throw new IllegalArgumentException("Invalid key metadata");
        }
        try {
            if (!hsm.validateKeyUnderLmk(
                    keyType, keyUnderLmk, kcv, keyLength)) {
                throw new IllegalArgumentException(
                        "Key KCV does not match the key under LMK");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Key cannot be validated by the configured HSM", e);
        }
    }
}
