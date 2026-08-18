package com.staging.sg.softpos;

import static com.staging.sg.softpos.contracts.SoftPosContracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.staging.sg.softpos.domain.*;
import com.staging.sg.softpos.repository.SoftPosRepositories.*;
import com.staging.sg.softpos.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class PaymentServiceTest {
    private DeviceRepository devices; private TransactionRepository transactions; private RouteRepository routes;
    private PosServerConnector connector; private PaymentService service; private SoftPosDevice device; private SoftPosPosServerRoute route;
    @BeforeEach void setup() {
        devices = mock(DeviceRepository.class); transactions = mock(TransactionRepository.class); routes = mock(RouteRepository.class); connector = mock(PosServerConnector.class);
        when(connector.mode()).thenReturn(PosServerMode.REST_JSON); service = new PaymentService(devices, transactions, routes, List.of(connector));
        device = SoftPosDevice.activate("MEMBER-A", "123456789012345", "OUTLET-1", "12345678", "fp", "pk", "1.0"); device.attest(Instant.now().plusSeconds(60));
        route = SoftPosPosServerRoute.configured("MEMBER-A", "LAB", PosServerMode.REST_JSON, "http://localhost:8530", 1000, 3000, true);
        when(devices.findByDeviceIdAndMemberId(device.getDeviceId(), "MEMBER-A")).thenReturn(Optional.of(device));
        when(routes.findByMemberIdAndEnvironmentAndActiveTrue("MEMBER-A", "LAB")).thenReturn(Optional.of(route));
        when(transactions.findByMemberIdAndIdempotencyKey(eq("MEMBER-A"), anyString())).thenReturn(Optional.empty());
    }
    @Test void approvedPaymentUsesConfiguredConnectorOnce() throws Exception {
        when(connector.exchange(any(), eq(route))).thenReturn(new PosServerPaymentResult(TransactionStatus.APPROVED, "00", "ABC123"));
        PaymentResponse result = service.pay("MEMBER-A", device.getDeviceId(), "LAB", request("TX-1", "IDEM-1"));
        assertEquals(TransactionStatus.APPROVED, result.status()); assertEquals("00", result.responseCode()); verify(connector, times(1)).exchange(any(), eq(route));
    }
    @Test void unknownConnectorOutcomeIsNotResubmitted() throws Exception {
        when(connector.exchange(any(), eq(route))).thenThrow(new IllegalStateException("timeout"));
        PaymentResponse result = service.pay("MEMBER-A", device.getDeviceId(), "LAB", request("TX-2", "IDEM-2"));
        assertEquals(TransactionStatus.UNKNOWN, result.status()); verify(connector, times(1)).exchange(any(), eq(route));
    }
    @Test void idempotentReplayReturnsStoredDecisionWithoutConnector() throws Exception {
        SoftPosTransaction previous = SoftPosTransaction.received("MEMBER-A", device.getDeviceId(), request("TX-3", "IDEM-3"), "hash");
        previous.complete(TransactionStatus.APPROVED, "00", "ABC123");
        when(transactions.findByMemberIdAndIdempotencyKey("MEMBER-A", "IDEM-3")).thenReturn(Optional.of(previous));
        PaymentResponse result = service.pay("MEMBER-A", device.getDeviceId(), "LAB", request("TX-3", "IDEM-3"));
        assertTrue(result.idempotentReplay()); verify(connector, never()).exchange(any(), any());
    }
    @Test void memberCannotUseAnotherMembersDevice() {
        when(devices.findByDeviceIdAndMemberId(device.getDeviceId(), "MEMBER-B")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.pay("MEMBER-B", device.getDeviceId(), "LAB", request("TX-4", "IDEM-4")));
    }
    private static PaymentRequest request(String tx, String idempotency) { return new PaymentRequest(tx, idempotency, AcceptanceChannel.NFC, 1250, "MAD", "LABREF:APPROVED_CARD", "INT-1", Map.of()); }
}
