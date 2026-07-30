package com.staging.sg.common.iso.crypto;

import com.staging.sg.common.iso.SwamPackager;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwamMacBuilderTest {
    private final SwamPackager packager = new SwamPackager();

    @Test
    void reproducesM6RawBufferForMakPush899() throws Exception {
        ISOMsg message = message("1804", "899");
        message.set(7, "2607291511");
        message.set(11, "580401");
        message.set(12, "260729151123");
        message.set(25, "0000");
        message.set(33, "300853");
        message.set(37, "621015260729");
        message.set(48, "P10007XABCDEF");

        assertEquals(
                "2607291511580401260729151123899000006300853"
                        + "621015260729013P10007XABCDEF",
                ascii(SwamMacBuilder.build(message)));
    }

    @Test
    void buildsDocumented96ByteAcknowledgementWithPrefixes() throws Exception {
        ISOMsg ack = message("1814", "811");
        ack.set(7, "2607291511");
        ack.set(11, "580001");
        ack.set(12, "260729151123");
        ack.set(33, "300853");
        ack.set(37, "621015260729");
        ack.set(39, "800");
        ack.set(48, "P16033X12345678901234567890123456789012");

        byte[] buffer = SwamMacBuilder.build(ack);

        assertEquals(96, buffer.length);
        assertEquals(
                "260729151158000126072915112381106300853"
                        + "621015260729800039P16033X12345678901234567890123456789012",
                ascii(buffer));
    }

    @Test
    void signOnOmitsVariableLengthPrefixes() throws Exception {
        ISOMsg signOn = message("1804", "801");
        signOn.set(7, "2607291611");
        signOn.set(11, "000001");
        signOn.set(33, "300853");

        assertEquals("2607291611000001801300853",
                ascii(SwamMacBuilder.build(signOn)));
    }

    private ISOMsg message(String mti, String function) throws Exception {
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI(mti);
        message.set(24, function);
        return message;
    }

    private static String ascii(byte[] value) {
        return new String(value, StandardCharsets.ISO_8859_1);
    }
}
