package com.staging.sg.common.iso;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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

    @Test
    void encodesExactWay4F20TwoKeyEnvelope() {
        var tpk = new WayPosKeyExchangeCodec.KeyBlock(
                "27", "TPK", null, null, "00", "TPMK",
                observedBlock('1'), "2", "0", null, null);
        var tak = new WayPosKeyExchangeCodec.KeyBlock(
                "27", "TAK", null, null, "00", "TAMK",
                observedBlock('2'), "2", "0", null, null);

        byte[] encoded = WayPosKeyExchangeCodec.encodeWay4F20Response(
                List.of(tak, tpk));

        assertEquals(292, encoded.length);
        var groups = WayPosBerTlv.decode(encoded);
        assertEquals(List.of(0xFF01, 0xFF02),
                groups.stream().map(WayPosBerTlv.Tlv::tag).toList());
        assertEquals(List.of(0xDF24, 0xDF20, 0xDF25, 0xDF28, 0xDF40, 0xDF41),
                WayPosBerTlv.decode(groups.get(0).value()).stream()
                        .map(WayPosBerTlv.Tlv::tag).toList());
        var decoded = WayPosKeyExchangeCodec.decodeResponse(encoded);
        assertEquals("TPK", decoded.get(0).keyType());
        assertEquals("TAK", decoded.get(1).keyType());
        assertEquals("27", decoded.get(0).keyId());
        assertEquals("2", decoded.get(0).keyBlockFormat());
    }

    private static byte[] observedBlock(char fill) {
        return ("D0112" + String.valueOf(fill).repeat(107))
                .getBytes(StandardCharsets.US_ASCII);
    }
}
