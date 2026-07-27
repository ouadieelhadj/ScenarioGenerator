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
    void everyLogicalTrailerUsesPublishedPositionsAndRoundTrips() throws Exception {
        int sequence = 20;
        for (String code : new String[] {"93", "95", "97", "99", "81"}) {
            byte[] packed = codec.pack(LisRecordFactory.logicalTrailer(
                    code, sequence++, java.util.Map.of(4, 7L, 11, 3L, 22, 12L, 41, 9L)));
            String record = new String(packed, StandardCharsets.US_ASCII);

            assertEquals(256, packed.length);
            assertEquals(code, record.substring(0, 2));
            assertEquals("000007", record.substring(9, 15));
            assertEquals("000003", record.substring(51, 57));
            // LIS 4.13 places F022 at 118 and F023 at 120 despite declaring F022 n6.
            assertEquals("12", record.substring(117, 119));
            assertEquals("000000", record.substring(119, 125));
            assertEquals("000009", record.substring(227, 233));
            assertEquals(" ".repeat(23), record.substring(233));
            assertArrayEquals(packed, codec.pack(codec.unpack(packed)));
        }
    }

    @Test
    void logicalTrailerRejectsCountersThatDoNotFitPublishedPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> LisRecordFactory.logicalTrailer("99", 3, java.util.Map.of(22, 100L)));
        assertThrows(IllegalArgumentException.class,
                () -> LisRecordFactory.logicalTrailer("99", 3, java.util.Map.of(42, 1L)));
    }

    @Test
    void rejectsWrongPhysicalLength() {
        assertThrows(ISOException.class, () -> codec.unpack(new byte[255]));
        assertThrows(ISOException.class, () -> codec.unpack(new byte[257]));
    }

    @Test
    void rejectsUnsupportedTcTcrBeforeParsingPayload() {
        byte[] record = "49".concat("000001").concat("0")
                .concat(" ".repeat(247)).getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> codec.unpack(record));
    }

    @Test
    void financialTcr0AndTcr1AreExactly256BytesAndRoundTrip() throws Exception {
        ISOMsg tcr0 = new ISOMsg();
        String[] values0 = {
                "05", "000010", "0", "0", "0000000001", "MERCHANT",
                "CASABLANCA", "MAR", "5999", " ", "  ", "2", "TERM0001",
                "1", "000", "INVOICE", "0000", "000000", " ", "1",
                "4000001234567899", "2812", "2", "I", "00", "00000", ""
        };
        for (int field = 0; field < values0.length; field++) tcr0.set(field, values0[field]);
        byte[] packed0 = codec.pack(tcr0);
        assertEquals(256, packed0.length);
        assertArrayEquals(packed0, codec.pack(codec.unpack(packed0)));

        ISOMsg tcr1 = new ISOMsg();
        String[] values1 = {
                "05", "000011", "1", "230726", "654321", "2", "0",
                "ACQ-REF-000000000000001", "00008800", " ", "2", "000000001000",
                "00012300", " ", "2", "000000001000", "000000001000",
                "000000000000", "230726", "230726", "   ", " ", "00", "0000",
                "000", "000000", "620414260723", "145135", "504", "504",
                "000000000000", "000000000000", " ", "000000000000",
                "000000000", "000000000", "0000000", ""
        };
        for (int field = 0; field < values1.length; field++) tcr1.set(field, values1[field]);
        byte[] packed1 = codec.pack(tcr1);
        assertEquals(256, packed1.length);
        assertArrayEquals(packed1, codec.pack(codec.unpack(packed1)));
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
