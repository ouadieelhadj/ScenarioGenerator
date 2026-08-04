package com.staging.sg.waypos.server.network;

import com.staging.sg.common.iso.WayPosKeyExchangeCodec;
import com.staging.sg.common.iso.WayPosPackager;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WayPosSafeMessageTraceTest {

    @Test
    void namesRkiAndMasksCardAndCryptographicMaterial() throws Exception {
        ISOMsg message = new ISOMsg();
        message.setPackager(new WayPosPackager());
        message.setMTI("0800");
        message.set(2, "5321960000003348");
        message.set(3, "960000");
        message.set(7, "0803173909");
        message.set(11, "000244");
        message.set(41, "12488881");
        message.set(48, WayPosKeyExchangeCodec.encodeStatusDetails(List.of(
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "28", "0", "TAMK", "A1B2C3", "C", "0"),
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "28", "0", "TPMK", "D4E5F6", "C", "0"))));
        message.set(52, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        message.set(64, new byte[] {9, 10, 11, 12});

        String trace = WayPosSafeMessageTrace.renderReceived(message);

        assertTrue(trace.contains("WAYPOS MESSAGE RECEIVED [INCOMING]"));
        assertTrue(trace.contains("DIRECTION=INCOMING"));
        assertTrue(trace.contains("RKI INITIAL KEY CHANGE (0800/960000)"));
        assertTrue(trace.contains("DE3=960000"));
        assertTrue(trace.contains("type=TAMK,id=28,status=0,kcv=PRESENT"));
        assertTrue(trace.contains("MASKED_FIELDS=2,48,52,64"));
        assertTrue(trace.contains("BUFFER DUMP SANITIZED"));
        assertTrue(trace.contains("FIELD DUMP SANITIZED"));
        assertTrue(trace.contains("- FLD (048)"));
        assertTrue(trace.contains("[<MASKED>]"));
        assertFalse(trace.contains("5321960000003348"));
        assertFalse(trace.contains("A1B2C3"));
        assertFalse(trace.contains("D4E5F6"));
        assertFalse(trace.contains("0102030405060708"));
    }

    @Test
    void tracesOutgoingResponseWithCorrelationAndResponseCode() throws Exception {
        ISOMsg response = new ISOMsg();
        response.setPackager(new WayPosPackager());
        response.setMTI("0810");
        response.set(3, "960000");
        response.set(7, "0804092718");
        response.set(11, "000248");
        response.set(37, "621701000248");
        response.set(39, "00");
        response.set(41, "12488881");
        response.set(48, new byte[] {1, 2, 3, 4, 5, 6});

        String trace = WayPosSafeMessageTrace.renderOutgoing(response);

        assertTrue(trace.contains("WAYPOS MESSAGE SENT [OUTGOING]"));
        assertTrue(trace.contains("DIRECTION=OUTGOING"));
        assertTrue(trace.contains("STAN=000248"));
        assertTrue(trace.contains("RRN=621701000248"));
        assertTrue(trace.contains("TERMINAL=12488881"));
        assertTrue(trace.contains("RC=00"));
        assertTrue(trace.contains("MASKED_FIELDS=48"));
        assertFalse(trace.contains("010203040506"));
    }
}
