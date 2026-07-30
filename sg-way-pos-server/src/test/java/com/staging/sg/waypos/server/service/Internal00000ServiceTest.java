package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.waypos.server.domain.PosCard;
import com.staging.sg.waypos.server.domain.PosAuthorization;
import com.staging.sg.waypos.server.domain.PosHold;
import com.staging.sg.waypos.server.repository.PosAuthorizationRepository;
import com.staging.sg.waypos.server.repository.PosCardRepository;
import com.staging.sg.waypos.server.repository.PosHoldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Internal00000ServiceTest {
    @Test
    void movesP2pFundsAtomicallyBetweenTwoLocalCards() {
        PosCardRepository cards = mock(PosCardRepository.class);
        PanProtectionService protection = mock(PanProtectionService.class);
        WayPosLocalPinService pin = mock(WayPosLocalPinService.class);
        WayPosLocalEmvService emv = mock(WayPosLocalEmvService.class);
        PosCard source = card("source", 10_000);
        PosCard target = card("target", 2_000);
        when(protection.hash("5321962145453348")).thenReturn("a");
        when(protection.hash("5321969999990001")).thenReturn("b");
        when(cards.findLockedByPanHash("a")).thenReturn(Optional.of(source));
        when(cards.findLockedByPanHash("b")).thenReturn(Optional.of(target));
        when(pin.verify(any(), any()))
                .thenReturn(WayPosLocalPinService.Verification.NOT_PRESENT);
        when(emv.validate(any(), any()))
                .thenReturn(WayPosLocalEmvService.Validation.notPresent());

        Internal00000Service service = new Internal00000Service(
                cards, mock(PosAuthorizationRepository.class),
                mock(PosHoldRepository.class), protection, pin, emv);
        var response = service.process(request());

        assertEquals("00", response.posResponseCode());
        assertEquals(9_000, source.getAvailableBalance());
        assertEquals(3_000, target.getAvailableBalance());
    }

    @Test
    void enrolsLocalPinFromEncryptedDe31Block() {
        PosCardRepository cards = mock(PosCardRepository.class);
        PanProtectionService protection = mock(PanProtectionService.class);
        WayPosLocalPinService pin = mock(WayPosLocalPinService.class);
        WayPosLocalEmvService emv = mock(WayPosLocalEmvService.class);
        PosCard card = PosCard.provisioned(
                "card", "532196******3348", "2912", "504", 10_000,
                null, 1, null, null, null, null, null);
        when(protection.hash("5321962145453348")).thenReturn("card");
        when(cards.findLockedByPanHash("card")).thenReturn(Optional.of(card));
        when(pin.verify(any(), any()))
                .thenReturn(WayPosLocalPinService.Verification.NOT_PRESENT);
        when(pin.updatePvv(any(), any(), any()))
                .thenReturn(WayPosLocalPinService.PinUpdate.UPDATED);
        when(emv.validate(any(), any()))
                .thenReturn(WayPosLocalEmvService.Validation.notPresent());

        Internal00000Service service = new Internal00000Service(
                cards, mock(PosAuthorizationRepository.class),
                mock(PosHoldRepository.class), protection, pin, emv);
        var response = service.process(pinEnrolmentRequest());

        assertEquals("00", response.posResponseCode());
        verify(pin).updatePvv(
                any(), any(), org.mockito.ArgumentMatchers.eq("1122334455667788"));
        verify(cards).save(card);
    }

    @Test
    void afdCompletionCapturesFinalAmountAndReleasesHoldRemainder() {
        PosCardRepository cards = mock(PosCardRepository.class);
        PosAuthorizationRepository authorizations =
                mock(PosAuthorizationRepository.class);
        PosHoldRepository holds = mock(PosHoldRepository.class);
        PanProtectionService protection = mock(PanProtectionService.class);
        PosCard card = card("card", 10_000);
        card.reserve(1_000);
        PosHold hold = PosHold.active("tx-original", "card", 1_000);
        PosAuthorization original = PosAuthorization.received(
                "tx-original", "idem-original", "0100", "000000",
                "532196******3348", "card", 1_000L, "504",
                "000001", "123456000001", "TERM0001",
                "MERCHANT0000001", "000123", null,
                "AUTHORIZATION", null);
        original.complete("00000", "APPROVED", "00", "123456");
        when(authorizations
                .findFirstByRrnAndTransactionIdNotOrderByCreatedAtDesc(
                        "123456000001", "tx-afd"))
                .thenReturn(Optional.of(original));
        when(holds.findLockedByTransactionId("tx-original"))
                .thenReturn(Optional.of(hold));
        when(cards.findLockedByPanHash("card")).thenReturn(Optional.of(card));

        Internal00000Service service = new Internal00000Service(
                cards, authorizations, holds, protection,
                mock(WayPosLocalPinService.class),
                mock(WayPosLocalEmvService.class));
        var response = service.process(afdCompletionRequest());

        assertEquals("00", response.posResponseCode());
        assertEquals(9_200, card.getAvailableBalance());
        assertEquals(false, hold.isActive());
    }

    @Test
    void tipCompletionDebitsOnlyTipAndUpdatesOriginalBatchAmount() {
        PosCardRepository cards = mock(PosCardRepository.class);
        PosAuthorizationRepository authorizations =
                mock(PosAuthorizationRepository.class);
        PanProtectionService protection = mock(PanProtectionService.class);
        PosCard card = card("card", 9_000);
        PosAuthorization original = PosAuthorization.received(
                "tx-purchase", "idem-purchase", "0200", "000000",
                "532196******3348", "card", 1_000L, "504",
                "000001", "123456000009", "TERM0001",
                "MERCHANT0000001", "000123", null,
                "PURCHASE_OR_PAYMENT", null);
        original.complete("00000", "APPROVED", "00", "123456");
        when(authorizations
                .findFirstByRrnAndTransactionIdNotOrderByCreatedAtDesc(
                        "123456000009", "tx-tip"))
                .thenReturn(Optional.of(original));
        when(protection.hash("5321962145453348")).thenReturn("card");
        when(cards.findLockedByPanHash("card")).thenReturn(Optional.of(card));
        Internal00000Service service = new Internal00000Service(
                cards, authorizations, mock(PosHoldRepository.class),
                protection, mock(WayPosLocalPinService.class),
                mock(WayPosLocalEmvService.class));

        var response = service.process(tipCompletionRequest());

        assertEquals("00", response.posResponseCode());
        assertEquals(8_900, card.getAvailableBalance());
        assertEquals(1_100L, original.getAmountMinor());
        verify(authorizations).save(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MINI_STATEMENT",
            "CASH_BY_CODE",
            "LOYALTY_PROGRAM_REQUEST",
            "UTILITY_PAYMENT_AUTHORIZATION",
            "UTILITY_PAYMENT",
            "BILL_PAYMENT_AUTHORIZATION",
            "BILL_PAYMENT_ADVICE",
            "INFORMATION_INQUIRY",
            "AUTHORIZATION_OTHER",
            "FINANCIAL_OTHER"
    })
    void refusesUnconnectedSpecializedOperationWithoutTouchingFunds(
            String operationName) {
        PosCardRepository cards = mock(PosCardRepository.class);
        PosAuthorizationRepository authorizations =
                mock(PosAuthorizationRepository.class);
        PosHoldRepository holds = mock(PosHoldRepository.class);
        PanProtectionService protection = mock(PanProtectionService.class);
        WayPosLocalPinService pin = mock(WayPosLocalPinService.class);
        WayPosLocalEmvService emv = mock(WayPosLocalEmvService.class);
        Internal00000Service service = new Internal00000Service(
                cards, authorizations, holds, protection, pin, emv);

        var response = service.process(unconnectedRequest(operationName));

        assertEquals("96", response.posResponseCode());
        verifyNoInteractions(
                cards, authorizations, holds, protection, pin, emv);
    }

    private static PosCard card(String hash, long balance) {
        return PosCard.provisioned(
                hash, "532196******3348", "2912", "504", balance,
                null, null, null, null, null, null, null);
    }

    private static RoutingTransactionRequest request() {
        return new RoutingTransactionRequest(
                "1.0", "tx-p2p", "corr-p2p", "idem-p2p", "DEBIT",
                "0200", "480000", "5321962145453348", "2912",
                "000000001000", "504", "000001", "123456000001",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of("operationName", "P2P_CARD_TO_CARD",
                        "targetAccount", "5321969999990001"));
    }

    private static RoutingTransactionRequest pinEnrolmentRequest() {
        return new RoutingTransactionRequest(
                "1.0", "tx-pin", "corr-pin", "idem-pin", "CONTROL",
                "0100", "920000", "5321962145453348", "2912",
                "000000000000", "504", "000002", "123456000002",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of(
                        "operationName", "PIN_MANAGEMENT",
                        "securityAdditionalData", "018111122334455667788",
                        "privateData63", "007PC20001"));
    }

    private static RoutingTransactionRequest afdCompletionRequest() {
        return new RoutingTransactionRequest(
                "1.0", "tx-afd", "corr-afd", "idem-afd", "CAPTURE",
                "0220", "000000", "5321962145453348", "2912",
                "000000000800", "504", "000003", "123456000001",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of("operationName", "AFD_COMPLETION", "networkId", "102"));
    }

    private static RoutingTransactionRequest tipCompletionRequest() {
        return new RoutingTransactionRequest(
                "1.0", "tx-tip", "corr-tip", "idem-tip", "CAPTURE",
                "0220", "020000", "5321962145453348", "2912",
                "000000001100", "504", "000004", "123456000009",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of(
                        "operationName", "TIP_PURCHASE_COMPLETION",
                        "networkId", "202",
                        "privateData63", "01438000000000100"));
    }

    private static RoutingTransactionRequest unconnectedRequest(
            String operationName) {
        return new RoutingTransactionRequest(
                "1.0", "tx-unsupported", "corr-unsupported",
                "idem-unsupported", "DEBIT",
                "0200", "520000", "5321962145453348", "2912",
                "000000001000", "504", "000005", "123456000005",
                "TERM0001", "MERCHANT0000001", null, null, null,
                Map.of("operationName", operationName));
    }
}
