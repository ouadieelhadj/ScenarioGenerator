package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.waypos.server.domain.PosTerminalProfile;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WayPosSystemMessageServiceTest {
    @Test
    void failedReconciliationRequiresUploadThenAdviceClosesBatch() throws Exception {
        PosTerminalProfile terminal = PosTerminalProfile.provisioned(
                "TERM0001", "MERCHANT0000001", true,
                "BIN", true, "000123");
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        WayPosReconciliationService reconciliation =
                mock(WayPosReconciliationService.class);
        when(terminals.findLockedByTerminalId("TERM0001"))
                .thenReturn(Optional.of(terminal));
        when(reconciliation.matches(
                eq("TERM0001"), eq("000123"), any(), anyBoolean()))
                .thenReturn(false, true);
        when(reconciliation.recordBatchUpload(
                any(), eq("TERM0001"), eq("000123"))).thenReturn(true);
        WayPosSystemMessageService service = new WayPosSystemMessageService(
                terminals, mock(WayPosKeyExchangeService.class), reconciliation,
                mock(WayPosFileUpdateService.class));

        ISOMsg failed = service.process(reconciliation("0500"));
        assertEquals("0510", failed.getMTI());
        assertEquals("95", failed.getString(39));
        assertEquals("BATCH_UPLOAD_REQUIRED", terminal.getBatchStatus());

        ISOMsg upload = service.process(message("0320", "000000"));
        assertEquals("0330", upload.getMTI());
        assertEquals("00", upload.getString(39));

        ISOMsg closed = service.process(reconciliation("0520"));
        assertEquals("0530", closed.getMTI());
        assertEquals("00", closed.getString(39));
        assertEquals("000123", closed.getString(60));
        assertEquals("000124", terminal.getBatchId());
        assertTrue(terminal.acceptsFinancialTransactions());
    }

    @Test
    void keyConfirmationAppliesTerminalStatusesBeforeResponding() throws Exception {
        PosTerminalProfile terminal = PosTerminalProfile.provisioned(
                "TERM0001", "MERCHANT0000001", true,
                "BIN", true, "000123");
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        when(terminals.findLockedByTerminalId("TERM0001"))
                .thenReturn(Optional.of(terminal));
        WayPosKeyExchangeService keyExchange =
                mock(WayPosKeyExchangeService.class);
        WayPosSystemMessageService service = new WayPosSystemMessageService(
                terminals, keyExchange, mock(WayPosReconciliationService.class),
                mock(WayPosFileUpdateService.class));
        ISOMsg request = message("0800", "930000");

        ISOMsg response = service.process(request);

        verify(keyExchange).confirm(request, terminal);
        assertEquals("0810", response.getMTI());
        assertEquals("00", response.getString(39));
    }

    private static ISOMsg reconciliation(String mti) throws Exception {
        ISOMsg value = message(mti, "920000");
        value.set(60, "000123");
        return value;
    }

    private static ISOMsg message(String mti, String processingCode)
            throws Exception {
        ISOMsg value = new ISOMsg();
        value.setPackager(new WayPosPackager());
        value.setMTI(mti);
        value.set(3, processingCode);
        value.set(7, "0730100000");
        value.set(11, "123456");
        value.set(41, "TERM0001");
        value.set(63, "006SV1.0.0");
        return value;
    }
}
