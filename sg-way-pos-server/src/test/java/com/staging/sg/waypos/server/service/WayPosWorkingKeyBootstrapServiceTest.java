package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WayPosWorkingKeyBootstrapServiceTest {
    private static final String UNDER_LMK = "00112233445566778899AABBCCDDEEFF";

    @Test
    void activatesTakOnlyAfterHsmKcvValidation() throws Exception {
        PosTerminalProfile terminal = terminal();
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        JposHsmService hsm = mock(JposHsmService.class);
        when(terminals.findLockedByTerminalId("TERM0001"))
                .thenReturn(Optional.of(terminal));
        when(hsm.validateKeyUnderLmk(
                "TAK", UNDER_LMK, "A1B2C3", 16)).thenReturn(true);
        when(terminals.save(terminal)).thenReturn(terminal);

        PosTerminalProfile result = new WayPosWorkingKeyBootstrapService(
                terminals, hsm).activate(
                "TERM0001", "tak", UNDER_LMK, "A1B2C3", 16);

        assertEquals(UNDER_LMK, result.getTakUnderLmk());
        assertEquals("A1B2C3", result.getTakKcv());
        assertEquals(16, result.getTakLength());
        verify(terminals).save(terminal);
    }

    @Test
    void rejectsMismatchedKcvWithoutPersisting() throws Exception {
        PosTerminalProfile terminal = terminal();
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        JposHsmService hsm = mock(JposHsmService.class);
        when(terminals.findLockedByTerminalId("TERM0001"))
                .thenReturn(Optional.of(terminal));
        when(hsm.validateKeyUnderLmk(
                "TPK", UNDER_LMK, "A1B2C3", 16)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> new WayPosWorkingKeyBootstrapService(terminals, hsm)
                        .activate("TERM0001", "TPK", UNDER_LMK, "A1B2C3", 16));

        verify(terminals, never()).save(terminal);
    }

    @Test
    void rejectsUnsupportedKeyTypeBeforeRepositoryAccess() {
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        JposHsmService hsm = mock(JposHsmService.class);

        assertThrows(IllegalArgumentException.class,
                () -> new WayPosWorkingKeyBootstrapService(terminals, hsm)
                        .activate("TERM0001", "ZMK", UNDER_LMK, "A1B2C3", 16));

        verify(terminals, never()).findLockedByTerminalId("TERM0001");
    }

    @Test
    void rejectsUnknownTerminal() {
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        when(terminals.findLockedByTerminalId("TERM0001"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new WayPosWorkingKeyBootstrapService(
                        terminals, mock(JposHsmService.class))
                        .activate("TERM0001", "TAK", UNDER_LMK, "A1B2C3", 16));
    }

    private static PosTerminalProfile terminal() {
        return PosTerminalProfile.provisioned(
                "TERM0001", "MERCHANT0000001", true,
                "BIN", true, "000001");
    }
}
