package com.staging.sg.common.iso;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WayPosPackagerTest {

    @Test
    void roundTripsPackedBcdAndBinaryFields() throws Exception {
        WayPosPackager packager = new WayPosPackager();
        ISOMsg source = new ISOMsg();
        source.setPackager(packager);
        source.setMTI("0200");
        source.set(2, "4123456789012345");
        source.set(3, "000000");
        source.set(4, "000000001000");
        source.set(7, "0729123456");
        source.set(11, "123456");
        source.set(14, "2912");
        source.set(22, "051");
        source.set(25, "00");
        source.set(37, "123456789012");
        source.set(41, "TERM0001");
        source.set(49, "504");
        source.set(52, new byte[] {1,2,3,4,5,6,7,8});
        source.set(55, new byte[] {(byte) 0x9F, 0x26, 0x01, 0x01});
        source.set(63, "005SV1.0");
        source.set(64, new byte[] {9,8,7,6});

        byte[] packed = source.pack();
        ISOMsg target = new ISOMsg();
        target.setPackager(packager);
        target.unpack(packed);

        assertEquals("0200", target.getMTI());
        assertEquals(source.getString(2), target.getString(2));
        assertEquals(source.getString(4), target.getString(4));
        assertEquals(source.getString(63), target.getString(63));
        assertArrayEquals(source.getBytes(52), target.getBytes(52));
        assertArrayEquals(source.getBytes(55), target.getBytes(55));
        assertArrayEquals(source.getBytes(64), target.getBytes(64));
    }
}
