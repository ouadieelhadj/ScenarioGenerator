package com.staging.sg.common.iso.crypto;

import com.staging.sg.common.iso.SwamPackager;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void buildsSignOffM6InputWithoutTheNetworkMacValue() throws Exception {
        ISOMsg signOff = message("1804", "802");
        signOff.set(7, "2607231400");
        signOff.set(11, "477618");
        signOff.set(12, "260723140042");
        signOff.set(25, "0000");
        signOff.set(33, "300853");
        signOff.set(37, "620414260723");
        signOff.set(128, new byte[]{0x11, 0x22, 0x33, 0x44});

        String input = ascii(SwamMacBuilder.build(signOff));

        assertEquals(
                "2607231400477618260723140042802000006300853"
                        + "620414260723",
                input);
        assertFalse(input.contains("11223344"));
    }

    @Test
    void usesTheSameBufferBeforePackingAndAfterUnpackingFixedCharacterFields()
            throws Exception {
        ISOMsg sender = message("1100", "100");
        sender.set(2, "5321962145453348");
        sender.set(3, "000000");
        sender.set(4, "000000001000");
        sender.set(7, "2608010628");
        sender.set(11, "000002");
        sender.set(12, "260801062801");
        sender.set(22, "010");
        sender.set(25, "59");
        sender.set(41, "ECOM0001");

        byte[] senderBuffer = SwamMacBuilder.build(sender);
        ISOMsg receiver = new ISOMsg();
        receiver.setPackager(packager);
        receiver.unpack(sender.pack());

        assertEquals("010         ", receiver.getString(22));
        assertEquals("0059", receiver.getString(25));
        assertArrayEquals(senderBuffer, SwamMacBuilder.build(receiver));
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
