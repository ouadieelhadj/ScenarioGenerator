package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.waypos.server.domain.PosTerminalKey;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WayPosSecurityServiceTest {
    @Test
    void rejectsExtendedOperationForBasicOnlyTerminal() throws Exception {
        PosTerminalProfile profile = PosTerminalProfile.provisioned(
                "TERM0001", "MERCHANT0000001", false,
                "BIN", false, "000001");
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        when(terminals.findById("TERM0001")).thenReturn(Optional.of(profile));
        WayPosSecurityService service = new WayPosSecurityService(
                terminals, mock(JposHsmService.class),
                mock(WayPosKeyExchangeService.class),
                mock(WayPosInitialKeyChangeAuthenticator.class));
        ISOMsg request = new ISOMsg();
        request.setMTI("0100");
        request.set(3, "910000");
        request.set(41, "TERM0001");

        var error = assertThrows(
                WayPosSecurityService.PosSecurityException.class,
                () -> service.validate(request));
        assertEquals("57", error.responseCode());
    }

    @Test
    void acceptsMaclessInitialRkiOnlyAfterMasterKeyKcvAuthentication()
            throws Exception {
        PosTerminalProfile profile = PosTerminalProfile.provisioned(
                "TERM0001", "MERCHANT0000001", true,
                "BIN", true, "000001");
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        when(terminals.findById("TERM0001")).thenReturn(Optional.of(profile));
        WayPosInitialKeyChangeAuthenticator initial =
                mock(WayPosInitialKeyChangeAuthenticator.class);
        ISOMsg request = systemMessage("960000");
        when(initial.authenticates(request)).thenReturn(true);
        WayPosSecurityService service = new WayPosSecurityService(
                terminals, mock(JposHsmService.class),
                mock(WayPosKeyExchangeService.class), initial);

        var validated = service.validate(request);

        assertEquals("TERM0001", validated.profile().getTerminalId());
        assertEquals(null, validated.takUnderLmk());
    }

    @Test
    void verifies930000WithDeliveredTakCandidate() throws Exception {
        PosTerminalProfile profile = PosTerminalProfile.provisioned(
                "TERM0001", "MERCHANT0000001", true,
                "BIN", true, "000001");
        profile.activateWorkingKey("TAK", "0011", "111111", 16);
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        when(terminals.findById("TERM0001")).thenReturn(Optional.of(profile));
        PosTerminalKey candidate = PosTerminalKey.pending(
                "TERM0001", "TAK", "27", "T", "222222",
                "00", "TAMK", new byte[] {1}, "2233", 16,
                "0", null);
        candidate.markDelivered();
        WayPosKeyExchangeService exchange = mock(WayPosKeyExchangeService.class);
        when(exchange.candidateAuthenticationKeys("TERM0001"))
                .thenReturn(java.util.List.of(candidate));
        JposHsmService hsm = mock(JposHsmService.class);
        when(hsm.generateWayPosMac(any(), eq("0011"), eq("111111"),
                eq(16), any())).thenReturn(new byte[] {9, 9, 9, 9});
        when(hsm.generateWayPosMac(any(), eq("2233"), eq("222222"),
                eq(16), any())).thenReturn(new byte[] {1, 2, 3, 4});
        WayPosSecurityService service = new WayPosSecurityService(
                terminals, hsm, exchange,
                mock(WayPosInitialKeyChangeAuthenticator.class));
        ISOMsg request = systemMessage("930000");
        request.set(64, new byte[] {1, 2, 3, 4});

        var validated = service.validate(request);

        assertEquals("2233", validated.takUnderLmk());
        assertEquals("222222", validated.kcv());
    }

    private static ISOMsg systemMessage(String processingCode) throws Exception {
        ISOMsg request = new ISOMsg();
        request.setPackager(new WayPosPackager());
        request.setMTI("0800");
        request.set(3, processingCode);
        request.set(7, "0803150000");
        request.set(11, "123456");
        request.set(41, "TERM0001");
        request.set(63, "007SV1.0.0");
        return request;
    }
}
