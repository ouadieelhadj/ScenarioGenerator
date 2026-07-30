package com.staging.sg.waypos.server.service;

import com.staging.sg.common.emv.McDmasEmv;
import com.staging.sg.common.iso.WayPosBerTlv;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.waypos.server.domain.PosCard;
import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WayPosLocalEmvServiceTest {
    @Test
    void validatesArqcRecordsAtcAndBuildsTag91Arpc() throws Exception {
        McDmasEmv emv = mock(McDmasEmv.class);
        WayPosLocalEmvService service = new WayPosLocalEmvService(emv);
        String arqc = "1122334455667788";
        when(emv.recomputeArqc(any())).thenReturn(arqc);
        when(emv.computeArpc(any(), eq(arqc), eq("3030")))
                .thenReturn("AABBCCDDEEFF0011");

        PosCard card = PosCard.provisioned(
                "hash", "532196******3348", "2912", "504", 100_000,
                null, null, "MDK_UNDER_LMK", "ABCDEF", 16,
                "00", "3030");
        RoutingTransactionRequest request = request(de55(arqc));

        WayPosLocalEmvService.Validation validation =
                service.validate(request, card);
        assertEquals(WayPosLocalEmvService.Status.VERIFIED, validation.status());
        assertEquals(1, card.getLastAtc());

        String responseDe55 = service.approvalResponse(card, validation);
        assertNotNull(responseDe55);
        var tag91 = WayPosBerTlv.decode(ISOUtil.hex2byte(responseDe55)).getFirst();
        assertEquals(0x91, tag91.tag());
        assertEquals("AABBCCDDEEFF00113030", ISOUtil.hexString(tag91.value()));
    }

    private static RoutingTransactionRequest request(String de55) {
        return new RoutingTransactionRequest(
                "1.0", "tx-1", "corr-1", "idem-1", "DEBIT",
                "0200", "000000", "5321962145453348", "2912",
                "000000001000", "504", "000001", "123456000001",
                "TERM0001", "MERCHANT0000001", null, de55,
                null, Map.of());
    }

    private static String de55(String arqc) {
        List<WayPosBerTlv.Tlv> tags = List.of(
                tlv(0x9F26, arqc),
                tlv(0x9F37, "01020304"),
                tlv(0x9F36, "0001"),
                tlv(0x95, "0000008000"),
                tlv(0x9A, "260730"),
                tlv(0x9C, "00"),
                tlv(0x9F02, "000000001000"),
                tlv(0x5F2A, "0504"),
                tlv(0x82, "1800"),
                tlv(0x9F1A, "0504"),
                tlv(0x9F10, "06010A03A00000"));
        return ISOUtil.hexString(WayPosBerTlv.encode(tags));
    }

    private static WayPosBerTlv.Tlv tlv(int tag, String value) {
        return new WayPosBerTlv.Tlv(tag, ISOUtil.hex2byte(value));
    }
}
