package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JposHsmServiceSwamMacTest {

    @Test
    void reproducesM6Algorithm01WithDoubleLengthKeyAndZeroPadding()
            throws Exception {
        String buffer = "2607291511"
                + "580401"
                + "260729151123"
                + "899"
                + "0000"
                + "06" + "300853"
                + "621015260729"
                + "039" + "P10033X00000000000000000000000000000000";

        byte[] mac = new JposHsmService().generateMacZmk(
                buffer.getBytes(StandardCharsets.US_ASCII),
                "0123456789ABCDEFFEDCBA9876543210");

        assertEquals(97, buffer.length());
        assertEquals("CBCF5FF83A7E598E", ISOUtil.hexString(mac));
    }
}
