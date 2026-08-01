package com.staging.sg.common.iso;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwamPackagerMacTest {
    private final SwamPackager packager = new SwamPackager();

    @Test
    void signOffCarriesDe128AndKeepsBit128OnTheWire() throws Exception {
        byte[] mac = new byte[]{0x11, 0x22, 0x33, 0x44};
        ISOMsg outbound = new ISOMsg();
        outbound.setPackager(packager);
        outbound.setMTI("1804");
        outbound.set(7, "2607231400");
        outbound.set(11, "477618");
        outbound.set(12, "260723140042");
        outbound.set(24, "802");
        outbound.set(25, "0000");
        outbound.set(33, "300853");
        outbound.set(37, "620414260723");
        outbound.set(128, mac);

        byte[] packed = outbound.pack();
        byte[] bitmap = Arrays.copyOfRange(packed, 4, 20);

        assertEquals("1804", new String(packed, 0, 4, StandardCharsets.US_ASCII));
        assertTrue((bitmap[0] & 0x80) != 0, "secondary bitmap indicator must be set");
        assertTrue((bitmap[15] & 0x01) != 0, "bit 128 must be set");

        ISOMsg inbound = new ISOMsg();
        inbound.setPackager(packager);
        inbound.unpack(packed);

        assertTrue(inbound.hasField(128));
        assertArrayEquals(mac, inbound.getBytes(128));
    }
}
