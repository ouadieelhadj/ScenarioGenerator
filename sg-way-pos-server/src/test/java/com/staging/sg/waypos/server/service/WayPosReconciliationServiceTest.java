package com.staging.sg.waypos.server.service;

import com.staging.sg.common.iso.WayPosPrivateData;
import com.staging.sg.waypos.server.domain.PosAuthorization;
import com.staging.sg.waypos.server.repository.PosAuthorizationRepository;
import com.staging.sg.waypos.server.repository.PosBatchUploadRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WayPosReconciliationServiceTest {
    @Test
    void comparesDebitAndCreditCountersAndAmounts() {
        PosAuthorizationRepository authorizations =
                mock(PosAuthorizationRepository.class);
        WayPosReconciliationService service = new WayPosReconciliationService(
                authorizations, mock(PosBatchUploadRepository.class));
        when(authorizations.findByTerminalIdAndBatchIdAndStatusIn(
                eq("TERM0001"), eq("000123"), any()))
                .thenReturn(List.of(
                        transaction("tx-d", "0200", "000000", 1_000),
                        transaction("tx-c", "0200", "250000", 500)));

        String exact = totals(1_000, 500);
        assertTrue(service.matches("TERM0001", "000123", exact, false));
        assertFalse(service.matches(
                "TERM0001", "000123", totals(1_001, 500), false));
    }

    private static PosAuthorization transaction(
            String id, String mti, String processingCode, long amount) {
        PosAuthorization value = PosAuthorization.received(
                id, "idem-" + id, mti, processingCode,
                "532196******3348", "hash", amount, "504",
                "000001", "123456000001", "TERM0001",
                "MERCHANT0000001", "000123", null, "TEST", null);
        value.complete("00000", "APPROVED", "00", "123456");
        return value;
    }

    private static String totals(long debit, long credit) {
        String groups = group("D", debit) + group("C", credit);
        return WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("PC", "20001"),
                new WayPosPrivateData.Item("28", groups)));
    }

    private static String group(String type, long amount) {
        return type + "1O" + "001" + "504" + "%012d".formatted(amount);
    }
}
