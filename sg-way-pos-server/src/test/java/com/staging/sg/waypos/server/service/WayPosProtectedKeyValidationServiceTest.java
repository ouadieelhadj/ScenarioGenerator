package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WayPosProtectedKeyValidationServiceTest {
    private static final String UNDER_LMK =
            "00112233445566778899AABBCCDDEEFF";

    @Test
    void acceptsOnlyAfterHsmKcvValidation() throws Exception {
        JposHsmService hsm = mock(JposHsmService.class);
        when(hsm.validateKeyUnderLmk(
                "PVK", UNDER_LMK, "A1B2C3", 16)).thenReturn(true);

        assertDoesNotThrow(() ->
                new WayPosProtectedKeyValidationService(hsm).requireValid(
                        "PVK", UNDER_LMK, "A1B2C3", 16));
    }

    @Test
    void rejectsMismatchedKcv() throws Exception {
        JposHsmService hsm = mock(JposHsmService.class);
        when(hsm.validateKeyUnderLmk(
                "MDK", UNDER_LMK, "A1B2C3", 16)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                new WayPosProtectedKeyValidationService(hsm).requireValid(
                        "MDK", UNDER_LMK, "A1B2C3", 16));
    }

    @Test
    void rejectsMalformedMetadataBeforeHsmCall() {
        assertThrows(IllegalArgumentException.class, () ->
                new WayPosProtectedKeyValidationService(
                        mock(JposHsmService.class)).requireValid(
                        "PVK", "NOT-HEX", "A1B2C3", 16));
    }
}
