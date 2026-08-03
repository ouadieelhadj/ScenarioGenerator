package com.staging.sg.waypos.server.network;

import com.staging.sg.common.iso.WayPosPackager;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WayPosIsoMapperTest {
    private final WayPosIsoMapper mapper = new WayPosIsoMapper();

    @Test
    void preservesAdviceClassWhenBuildingResponseMti() throws Exception {
        assertEquals("0230", responseMti("0220"));
        assertEquals("0430", responseMti("0420"));
    }

    @Test
    void repeatUsesSameIdempotencyKeyAsOriginalRequest() throws Exception {
        assertEquals(idempotencyKey("0200"), idempotencyKey("0201"));
        assertEquals(idempotencyKey("0220"), idempotencyKey("0221"));
    }

    @Test
    void generatesTwelveDigitRrnWhenRealTerminalOmitsDe37() throws Exception {
        ISOMsg request = new ISOMsg();
        request.setPackager(new WayPosPackager());
        request.setMTI("0200");
        request.set(7, "0724154116");
        request.set(11, "000217");

        ISOMsg response = mapper.toResponse(request,
                new RoutingTransactionResponse(
                        "tx", "DECLINED", "96", "96", null,
                        null, null, null, false, Map.of()));

        assertEquals("0210", response.getMTI());
        assertEquals(request.getString(7), response.getString(7));
        assertTrue(response.getString(37).matches("\\d{12}"));
    }

    private String responseMti(String requestMti) throws Exception {
        ISOMsg request = new ISOMsg();
        request.setPackager(new WayPosPackager());
        request.setMTI(requestMti);
        request.set(11, "123456");
        return mapper.toResponse(request, new RoutingTransactionResponse(
                "tx", "APPROVED", "00", "00", null,
                "00000", null, null, false, Map.of())).getMTI();
    }

    private String idempotencyKey(String mti) throws Exception {
        ISOMsg request = new ISOMsg();
        request.setMTI(mti);
        request.set(2, "5321960000003348");
        request.set(3, "000000");
        request.set(4, "000000001000");
        request.set(11, "123456");
        request.set(41, "TERM0001");
        return mapper.toRequest(request).idempotencyKey();
    }
}
