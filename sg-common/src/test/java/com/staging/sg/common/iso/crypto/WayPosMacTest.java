package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WayPosMacTest {

    private static final byte[] KEY =
            ISOUtil.hex2byte("0123456789ABCDEF");
    private static final byte[] DATA =
            "Now is the time for all ".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesOpenWayReferenceVectors() {
        assertVector(24, "70A30640", "D3FCABF4");
        assertVector(19, "12359511", "D5887762");
        assertVector(9, "5770223B", "DF105C31");
        assertVector(2, "C02D9550", "A7D2174C");
    }

    private static void assertVector(int length, String bin, String hex) {
        byte[] input = java.util.Arrays.copyOf(DATA, length);
        assertEquals(bin, ISOUtil.hexString(
                WayPosMac.calculate(KEY, input, WayPosMac.DataMode.BIN)));
        assertEquals(hex, ISOUtil.hexString(
                WayPosMac.calculate(KEY, input, WayPosMac.DataMode.HEX)));
    }
}
