package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.crypto.JposHsmService;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                mock(WayPosKeyExchangeService.class));
        ISOMsg request = new ISOMsg();
        request.setMTI("0100");
        request.set(3, "910000");
        request.set(41, "TERM0001");

        var error = assertThrows(
                WayPosSecurityService.PosSecurityException.class,
                () -> service.validate(request));
        assertEquals("57", error.responseCode());
    }
}
