package com.staging.sg.swam.lis.common.codec;

import com.staging.sg.swam.lis.common.model.LisRecordFactory;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LisRecordCodecTest {
    private LisRecordCodec codec;

    @BeforeEach
    void setUp() {
        codec = new LisRecordCodec(new LisPackagerRegistry());
    }

    @Test
    void tc90MatchesLis413PositionsAndRoundTrips() throws Exception {
        ISOMsg header = LisRecordFactory.fileHeader(
                1, "000123", "260726", 7, false, "000088");

        byte[] packed = codec.pack(header);
        String record = new String(packed, StandardCharsets.US_ASCII);

        assertEquals(256, packed.length);
        assertEquals("90", record.substring(0, 2));
        assertEquals("000001", record.substring(2, 8));
        assertEquals("0", record.substring(8, 9));
        assertEquals("000123", record.substring(9, 15));
        assertEquals("260726", record.substring(15, 21));
        assertEquals("007", record.substring(21, 24));
        assertEquals(" ", record.substring(24, 25));
        assertEquals("000088", record.substring(25, 31));
        assertEquals(" ".repeat(225), record.substring(31));

        ISOMsg decoded = codec.unpack(packed);
        assertArrayEquals(packed, codec.pack(decoded));
    }

    @Test
    void regeneratedTc90UsesRAtPosition25() throws Exception {
        byte[] packed = codec.pack(LisRecordFactory.fileHeader(
                1, "000123", "260726", 8, true, "000088"));
        assertEquals('R', (char) packed[24]);
    }

    @Test
    void tc91UsesExactCounterWidthsAndRoundTrips() throws Exception {
        byte[] packed = codec.pack(LisRecordFactory.fileTrailer(
                12, 12, java.util.Map.of(11, 3L, 20, 2L, 34, 1L)));
        String record = new String(packed, StandardCharsets.US_ASCII);

        assertEquals(256, packed.length);
        assertEquals("91", record.substring(0, 2));
        assertEquals("000012", record.substring(2, 8));
        assertEquals("000012", record.substring(9, 15));
        // F011 starts at position 52 (one-based) and is n6.
        assertEquals("000003", record.substring(51, 57));
        // F020 starts at position 106 (one-based) and is n4.
        assertEquals("0002", record.substring(105, 109));
        // F034 starts at position 162 (one-based) and is n6.
        assertEquals("000001", record.substring(161, 167));
        assertEquals(" ".repeat(65), record.substring(191));
        assertArrayEquals(packed, codec.pack(codec.unpack(packed)));
    }

    @Test
    void everyLogicalHeaderIsExactly256BytesAndRoundTrips() throws Exception {
        int sequence = 2;
        for (String code : new String[] {"92", "94", "96", "98", "80"}) {
            byte[] packed = codec.pack(LisRecordFactory.logicalHeader(code, sequence++));
            assertEquals(256, packed.length);
            assertEquals(code, new String(packed, 0, 2, StandardCharsets.US_ASCII));
            assertEquals('0', (char) packed[8]);
            assertEquals(" ".repeat(247),
                    new String(packed, 9, 247, StandardCharsets.US_ASCII));
            assertArrayEquals(packed, codec.pack(codec.unpack(packed)));
        }
    }

    @Test
    void rejectsWrongPhysicalLength() {
        assertThrows(ISOException.class, () -> codec.unpack(new byte[255]));
        assertThrows(ISOException.class, () -> codec.unpack(new byte[257]));
    }

    @Test
    void rejectsUnsupportedTcTcrBeforeParsingPayload() {
        byte[] record = "05".concat("000001").concat("0")
                .concat(" ".repeat(247)).getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> codec.unpack(record));
    }

    @Test
    void rejectsInvalidTc90BusinessValues() {
        assertThrows(IllegalArgumentException.class,
                () -> LisRecordFactory.fileHeader(2, "000123", "260726", 1, false, "000088"));
        assertThrows(IllegalArgumentException.class,
                () -> LisRecordFactory.fileHeader(1, "ABC123", "260726", 1, false, "000088"));
        assertThrows(IllegalArgumentException.class,
                () -> LisRecordFactory.fileHeader(1, "000123", "260726", 0, false, "000088"));
    }
}
