package com.staging.sg.common.iso;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WayPosKeyExchangeCodecTest {
    @Test
    void roundTripsTerminalKeyStatuses() {
        var expected = List.of(
                new WayPosKeyExchangeCodec.KeyStatus("01", "0", "TAK"),
                new WayPosKeyExchangeCodec.KeyStatus("02", "3", "TPK"));
        assertEquals(expected, WayPosKeyExchangeCodec.decodeStatuses(
                WayPosKeyExchangeCodec.encodeStatuses(expected)));
    }

    @Test
    void roundTripsMasterKeyStatusMetadata() {
        var expected = List.of(
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "00", "0", "TAMK", "A1B2C3", "C", "0"),
                new WayPosKeyExchangeCodec.KeyStatusDetails(
                        "00", "0", "TPMK", "D4E5F6", "C", "0"));

        assertEquals(expected, WayPosKeyExchangeCodec.decodeStatusDetails(
                WayPosKeyExchangeCodec.encodeStatusDetails(expected)));
    }

    @Test
    void marksResponseAsAnsiX917() {
        var expected = new WayPosKeyExchangeCodec.KeyBlock(
                "01", "TAK", "D5D44F", "T",
                "00", "TMK", new byte[] {1,2,3,4,5,6,7,8});
        byte[] encoded = WayPosKeyExchangeCodec.encodeResponse(List.of(expected));
        assertTrue(WayPosBerTlv.decode(encoded).getFirst().tag() == 0xFF01);
        var decoded = WayPosKeyExchangeCodec.decodeResponse(encoded).getFirst();
        assertEquals(expected.keyId(), decoded.keyId());
        assertEquals(expected.keyType(), decoded.keyType());
        assertTrue(Arrays.equals(expected.ansiX917Block(), decoded.ansiX917Block()));
    }

    @Test
    void splitsOnlyBetweenCompleteKeyGroups() {
        var keys = List.of(
                new WayPosKeyExchangeCodec.KeyBlock(
                        "01", "TAK", "D5D44F", "T", "00", "TMK", new byte[40]),
                new WayPosKeyExchangeCodec.KeyBlock(
                        "02", "TPK", "A1B2C3", "T", "00", "TMK", new byte[40]));
        var fields = WayPosKeyExchangeCodec.encodeResponseFields(keys, 100);
        assertEquals(2, fields.size());
        assertEquals(1, WayPosKeyExchangeCodec.decodeResponse(fields.get(0)).size());
        assertEquals(1, WayPosKeyExchangeCodec.decodeResponse(fields.get(1)).size());
    }

    @Test
    void decodesObservedThalesFormatWithoutTreatingItAsAnsiX917() {
        byte[] protectedBlock = new byte[112];
        var expected = new WayPosKeyExchangeCodec.KeyBlock(
                "27", "TPK", null, null, "00", "TPMK",
                protectedBlock, "2", "0", null, null);

        var decoded = WayPosKeyExchangeCodec.decodeResponse(
                WayPosKeyExchangeCodec.encodeResponse(List.of(expected))).getFirst();

        assertEquals("2", decoded.keyBlockFormat());
        assertEquals(112, decoded.ansiX917Block().length);
        assertEquals("TPMK", decoded.masterKeyType());
    }
}
