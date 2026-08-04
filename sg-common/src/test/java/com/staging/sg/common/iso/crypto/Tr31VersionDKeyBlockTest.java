package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tr31VersionDKeyBlockTest {
    @Test
    void matchesPublishedAes128VersionDVector() throws Exception {
        byte[] kbpk = ISOUtil.hex2byte(
                "F45185EADC5B799819DC8F4C3B58EC73");
        byte[] clear = ISOUtil.hex2byte(
                "3F419E1CB7079442AA37474C2EFBF8B8");

        String block = Tr31VersionDKeyBlock.wrap(
                kbpk, clear, "P0", "E", "00", "E", new byte[14]);

        assertEquals(
                "D0112P0TE00E0000"
                        + "13B674A99811C18AB8BCFB26D347F844"
                        + "9E68FC074858D85DC452E43910CDC2A5"
                        + "E9BFE75DC94415EC0A82072217D04E35",
                block);
        assertArrayEquals(clear,
                Tr31VersionDKeyBlock.unwrap(kbpk, block));
    }

    @Test
    void producesExactWay4LengthsForTakAndTpk() throws Exception {
        byte[] kbpk = ISOUtil.hex2byte(
                "00112233445566778899AABBCCDDEEFF"
                        + "0102030405060708");
        byte[] clear = ISOUtil.hex2byte(
                "0123456789ABCDEFFEDCBA9876543210");

        String tak = Tr31VersionDKeyBlock.wrap(
                kbpk, clear, "M3", "N", "28", "N");
        String tpk = Tr31VersionDKeyBlock.wrap(
                kbpk, clear, "P0", "N", "28", "N");

        assertEquals(112, tak.length());
        assertEquals(112, tpk.length());
        assertTrue(tak.startsWith("D0112M3TN28N0000"));
        assertTrue(tpk.startsWith("D0112P0TN28N0000"));
        assertArrayEquals(clear,
                Tr31VersionDKeyBlock.unwrap(kbpk, tak));
        assertArrayEquals(clear,
                Tr31VersionDKeyBlock.unwrap(kbpk, tpk));
    }
}
