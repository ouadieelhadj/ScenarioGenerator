package com.staging.sg.card.issuing.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtectedPanTest {
    @Test
    void representationNeverExposesVaultReference() {
        ProtectedPan value = new ProtectedPan(
                "vault-secret-reference", "532196******3348", "2912");

        assertFalse(value.toString().contains("vault-secret-reference"));
    }

    @Test
    void clearPanCannotBeUsedAsMaskedPan() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProtectedPan(
                        "vault-ref", "5321962145453348", "2912"));
    }
}
