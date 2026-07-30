package com.staging.sg.swam.acquirer.api;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwamRoutingControllerTest {

    @Test
    void mapsApprovedSidResponseAndArpc() throws Exception {
        SwamNetworkController network = mock(SwamNetworkController.class);
        SwamRoutingController controller = new SwamRoutingController(network);
        RoutingTransactionRequest request = request();
        when(network.sendRouted(request)).thenReturn(Map.of(
                "de39_action", "000",
                "de38_auth", "ABC123",
                "de55_response_hex", "910A00112233445566778899"));

        ResponseEntity<?> entity = controller.transact(request);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        RoutingTransactionResponse response = assertInstanceOf(
                RoutingTransactionResponse.class, entity.getBody());
        assertEquals("APPROVED", response.status());
        assertEquals("00", response.posResponseCode());
        assertEquals("000", response.networkResponseCode());
        assertEquals("SWAM_MEMBER", response.route());
        assertEquals("910A00112233445566778899", response.arpcHex());
        verify(network).sendRouted(request);
    }

    @Test
    void mapsSidInsufficientFundsToPosCode51() throws Exception {
        SwamNetworkController network = mock(SwamNetworkController.class);
        SwamRoutingController controller = new SwamRoutingController(network);
        RoutingTransactionRequest request = request();
        when(network.sendRouted(request)).thenReturn(Map.of(
                "de39_action", "116"));

        RoutingTransactionResponse response = assertInstanceOf(
                RoutingTransactionResponse.class,
                controller.transact(request).getBody());

        assertEquals("DECLINED", response.status());
        assertEquals("51", response.posResponseCode());
        assertEquals("116", response.networkResponseCode());
    }

    private static RoutingTransactionRequest request() {
        return new RoutingTransactionRequest(
                "1.0", "tx-1", "corr-1", "idem-1", "DEBIT", "0200",
                "000000", "5321962145453348", "2912",
                "000000001000", "504", "000001", "123456000001",
                "TERM0001", "MERCHANT000001", null,
                "9F26081122334455667788", null, Map.of());
    }
}
