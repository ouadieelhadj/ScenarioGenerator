package com.staging.sg.mc.dmas.member.api;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.mc.dmas.member.network.McDmasAdvice;
import com.staging.sg.mc.dmas.member.network.McDmasAuthorization;
import com.staging.sg.mc.dmas.member.network.McDmasReversal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McDmasRoutingControllerTest {

    @Test
    void routesFinancialRequestAndReturnsNetworkArpc() throws Exception {
        McDmasAuthorization authorization = mock(McDmasAuthorization.class);
        McDmasAdvice advice = mock(McDmasAdvice.class);
        McDmasReversal reversal = mock(McDmasReversal.class);
        McDmasRoutingController controller =
                new McDmasRoutingController(authorization, advice, reversal);
        RoutingTransactionRequest request = request(
                "DEBIT", "0200", "DMAS_MEMBER");
        when(authorization.sendRoutedAuthorization(
                eq("PURCHASE"), eq("0200"), eq("000000"),
                eq(request.pan()), eq(request.expiry()), eq(request.amount()),
                eq(request.currency()), any(byte[].class),
                eq(request.emvDataHex()), eq(request.terminalId()),
                eq(request.merchantId()), eq("051"), eq("00"),
                eq(request.rrn()))).thenReturn(Map.of(
                "approved", true,
                "de039_response_code", "00",
                "de038_authorization_code", "ABC123",
                "de055_response_hex", "910A00112233445566778899"));

        ResponseEntity<?> entity = controller.transact(request);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        RoutingTransactionResponse response = assertInstanceOf(
                RoutingTransactionResponse.class, entity.getBody());
        assertEquals("APPROVED", response.status());
        assertEquals("DMAS_MEMBER", response.route());
        assertEquals("ABC123", response.authorizationCode());
        assertEquals("910A00112233445566778899", response.arpcHex());
        assertEquals(request.amount(), response.approvedAmount());
    }

    @Test
    void routesReversalAdviceWithOriginalReferences() throws Exception {
        McDmasAuthorization authorization = mock(McDmasAuthorization.class);
        McDmasAdvice advice = mock(McDmasAdvice.class);
        McDmasReversal reversal = mock(McDmasReversal.class);
        McDmasRoutingController controller =
                new McDmasRoutingController(authorization, advice, reversal);
        RoutingTransactionRequest request = new RoutingTransactionRequest(
                "1.0", "rev-1", "corr-1", "idem-1", "REVERSAL", "0421",
                "000000", "5321962145453348", "2912",
                "000000001000", "504", "000002", "123456000001",
                "TERM0001", "MERCHANT000001", null, null, "tx-1",
                Map.of("originalStan", "000001",
                        "originalTransmissionDateTime", "0730113000"));
        when(reversal.sendReversalAdvice(
                request.pan(), request.amount(), request.processingCode(),
                "000001", "0730113000")).thenReturn(Map.of(
                "reversed", true, "de039_response_code", "00"));

        ResponseEntity<?> entity = controller.transact(request);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        RoutingTransactionResponse response = assertInstanceOf(
                RoutingTransactionResponse.class, entity.getBody());
        assertEquals("APPROVED", response.status());
        verify(reversal).sendReversalAdvice(
                request.pan(), request.amount(), request.processingCode(),
                "000001", "0730113000");
        verifyNoInteractions(authorization, advice);
    }

    @Test
    void rejectsPinBlockOutsideDmasPekDomain() {
        McDmasAuthorization authorization = mock(McDmasAuthorization.class);
        McDmasAdvice advice = mock(McDmasAdvice.class);
        McDmasReversal reversal = mock(McDmasReversal.class);
        McDmasRoutingController controller =
                new McDmasRoutingController(authorization, advice, reversal);

        ResponseEntity<?> entity = controller.transact(request(
                "DEBIT", "0200", "SWAM_MEMBER"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, entity.getStatusCode());
        verifyNoInteractions(authorization, advice, reversal);
    }

    private static RoutingTransactionRequest request(
            String operation, String mti, String pinDomain) {
        return new RoutingTransactionRequest(
                "1.0", "tx-1", "corr-1", "idem-1", operation, mti,
                "000000", "5321962145453348", "2912",
                "000000001000", "504", "000001", "123456000001",
                "TERM0001", "MERCHANT000001", "0011223344556677",
                "9F26081122334455667788", null,
                Map.of("pinBlockKeyDomain", pinDomain,
                        "entryMode", "051", "conditionCode", "00"));
    }
}
