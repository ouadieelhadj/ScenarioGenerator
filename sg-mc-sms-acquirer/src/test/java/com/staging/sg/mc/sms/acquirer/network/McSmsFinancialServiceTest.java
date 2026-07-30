package com.staging.sg.mc.sms.acquirer.network;

import com.staging.sg.common.iso.MastercardSmsPackagerEbcdic;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McSmsFinancialServiceTest {

    @Test
    void buildsFinancialMessageWithTranslatedPinAndEmvAndReturnsArpc()
            throws Exception {
        McSmsJposClient client = mock(McSmsJposClient.class);
        MastercardSmsPackagerEbcdic packager =
                new MastercardSmsPackagerEbcdic();
        when(client.getPackager()).thenReturn(packager);
        ISOMsg networkResponse = new ISOMsg();
        networkResponse.setPackager(packager);
        networkResponse.setMTI("0210");
        networkResponse.set(39, "00");
        networkResponse.set(38, "ABC123");
        networkResponse.set(55, ISOUtil.hex2byte(
                "910A00112233445566778899"));
        when(client.sendAndWait(any(ISOMsg.class), eq(30)))
                .thenReturn(networkResponse);
        McSmsFinancialService service = new McSmsFinancialService(client);
        RoutingTransactionRequest request = request(
                "DEBIT", "0200", "MASTERCARD_SMS");

        Map<String, Object> result = service.send(request);

        ArgumentCaptor<ISOMsg> sent = ArgumentCaptor.forClass(ISOMsg.class);
        verify(client).sendAndWait(sent.capture(), eq(30));
        ISOMsg message = sent.getValue();
        assertEquals("0200", message.getMTI());
        assertEquals(request.processingCode(), message.getString(3));
        assertEquals(request.terminalId(), message.getString(41));
        assertEquals(request.merchantId(), message.getString(42));
        assertArrayEquals(ISOUtil.hex2byte(request.pinBlockHex()),
                message.getBytes(52));
        assertArrayEquals(ISOUtil.hex2byte(request.emvDataHex()),
                message.getBytes(55));
        assertEquals(true, result.get("approved"));
        assertEquals("910A00112233445566778899",
                result.get("de55_response_hex"));
    }

    @Test
    void mapsReversalAdviceTo0420AndCarriesOriginalDataElements()
            throws Exception {
        McSmsJposClient client = mock(McSmsJposClient.class);
        MastercardSmsPackagerEbcdic packager =
                new MastercardSmsPackagerEbcdic();
        when(client.getPackager()).thenReturn(packager);
        ISOMsg networkResponse = new ISOMsg();
        networkResponse.setPackager(packager);
        networkResponse.setMTI("0430");
        networkResponse.set(39, "00");
        when(client.sendAndWait(any(ISOMsg.class), eq(30)))
                .thenReturn(networkResponse);
        McSmsFinancialService service = new McSmsFinancialService(client);
        RoutingTransactionRequest request = new RoutingTransactionRequest(
                "1.0", "rev-1", "corr-1", "idem-1", "REVERSAL", "0421",
                "000000", "5321962145453348", "2912",
                "000000001000", "504", "000002", "123456000001",
                "TERM0001", "MERCHANT000001", null, null, "tx-1",
                Map.of("originalDataElements",
                        "020000000107301130001234567890123456789012"));

        service.send(request);

        ArgumentCaptor<ISOMsg> sent = ArgumentCaptor.forClass(ISOMsg.class);
        verify(client).sendAndWait(sent.capture(), eq(30));
        assertEquals("0420", sent.getValue().getMTI());
        assertEquals(request.attributes().get("originalDataElements"),
                sent.getValue().getString(90));
    }

    @Test
    void rejectsPinBlockOutsideMastercardSmsPekDomain() {
        McSmsJposClient client = mock(McSmsJposClient.class);
        McSmsFinancialService service = new McSmsFinancialService(client);

        assertThrows(IllegalArgumentException.class,
                () -> service.send(request("DEBIT", "0200", "DMAS_MEMBER")));

        verifyNoInteractions(client);
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
