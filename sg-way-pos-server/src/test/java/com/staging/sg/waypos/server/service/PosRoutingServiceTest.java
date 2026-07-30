package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosRoutingServiceTest {
    @Test
    void allowsReversalNeededForRecoveryWhenBatchIsBlocked() {
        PosRouteResolver routes = mock(PosRouteResolver.class);
        Internal00000Service internal = mock(Internal00000Service.class);
        PosJournalService journal = mock(PosJournalService.class);
        when(journal.existing("idem")).thenReturn(Optional.empty());
        when(routes.resolve("5321962145453348")).thenReturn("00000");
        when(internal.process(any())).thenReturn(approved("tx"));
        PosRoutingService service = new PosRoutingService(
                routes, internal, mock(NetworkRoutingConnector.class), journal,
                mock(WayPosPinTranslationService.class),
                mock(PosTerminalProfileRepository.class),
                mock(PosRecoveryService.class));

        assertEquals("00", service.process(request("REVERSAL")).posResponseCode());
    }

    @Test
    void blocksNewDebitWhenTerminalBatchIsNotOpen() {
        PosJournalService journal = mock(PosJournalService.class);
        PosTerminalProfileRepository terminals =
                mock(PosTerminalProfileRepository.class);
        when(journal.existing("idem")).thenReturn(Optional.empty());
        when(terminals.findById("TERM0001")).thenReturn(Optional.empty());
        PosRoutingService service = new PosRoutingService(
                mock(PosRouteResolver.class), mock(Internal00000Service.class),
                mock(NetworkRoutingConnector.class), journal,
                mock(WayPosPinTranslationService.class), terminals,
                mock(PosRecoveryService.class));

        assertEquals("95", service.process(request("DEBIT")).posResponseCode());
    }

    private static RoutingTransactionRequest request(String effect) {
        return new RoutingTransactionRequest(
                "1.0", "tx", "corr", "idem", effect,
                "REVERSAL".equals(effect) ? "0420" : "0200", "000000",
                "5321962145453348", "2912", "000000001000", "504",
                "000001", "123456000001", "TERM0001",
                "MERCHANT0000001", null, null, null, Map.of());
    }

    private static RoutingTransactionResponse approved(String id) {
        return new RoutingTransactionResponse(
                id, "APPROVED", "00", "00", null,
                "00000", "000000001000", null, false, Map.of());
    }
}
