package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.domain.PosAuthorization;
import com.staging.sg.waypos.server.repository.PosAuthorizationRepository;
import com.staging.sg.waypos.server.repository.PosTerminalProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosJournalServiceTest {
    @Test
    void idempotentReplayPreservesEmvResponse() {
        PosAuthorizationRepository repository =
                mock(PosAuthorizationRepository.class);
        PosAuthorization value = transaction("tx", "idem", "0200");
        value.complete("00000", "APPROVED", "00", "123456", "910A010203");
        when(repository.findByIdempotencyKey("idem"))
                .thenReturn(Optional.of(value));
        PosJournalService service = service(repository);

        assertEquals("910A010203",
                service.existing("idem").orElseThrow().arpcHex());
    }

    @Test
    void successfulAutomaticReversalExcludesOriginalFromBatch() {
        PosAuthorizationRepository repository =
                mock(PosAuthorizationRepository.class);
        PosAuthorization original =
                transaction("original", "idem-original", "0200");
        original.complete("DMAS_MEMBER", "APPROVED", "00", "123456");
        when(repository
                .findFirstByRrnAndTransactionIdNotOrderByCreatedAtDesc(
                        "123456000001", "reversal"))
                .thenReturn(Optional.of(original));
        PosJournalService service = service(repository);
        RoutingTransactionRequest request = new RoutingTransactionRequest(
                "1.0", "reversal", "corr", "idem-reversal", "REVERSAL",
                "0420", "000000", "5321962145453348", "2912",
                "000000001000", "504", "000002", "123456000001",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of("operationName", "UNIVERSAL_REVERSAL",
                        "networkId", "402"));

        service.applyLinkedOutcome(request, approved("reversal"));

        assertEquals("AUTO_REVERSED", original.getStatus());
        verify(repository).save(original);
    }

    private static PosJournalService service(
            PosAuthorizationRepository repository) {
        return new PosJournalService(
                repository, mock(PanProtectionService.class),
                mock(PosTerminalProfileRepository.class));
    }

    private static PosAuthorization transaction(
            String id, String idempotency, String mti) {
        return PosAuthorization.received(
                id, idempotency, mti, "000000",
                "532196******3348", "hash", 1_000L, "504",
                "000001", "123456000001", "TERM0001",
                "MERCHANT0000001", "000123", null,
                "PURCHASE_OR_PAYMENT", null);
    }

    private static RoutingTransactionResponse approved(String id) {
        return new RoutingTransactionResponse(
                id, "APPROVED", "00", "00", "123456",
                "DMAS_MEMBER", "000000001000", null, false, Map.of());
    }
}
