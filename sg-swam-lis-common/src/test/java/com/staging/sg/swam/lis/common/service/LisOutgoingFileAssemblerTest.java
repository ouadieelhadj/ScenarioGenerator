package com.staging.sg.swam.lis.common.service;

import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.LisFinancialRecord;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LisOutgoingFileAssemblerTest {
    private final LisRecordCodec codec = new LisRecordCodec(new LisPackagerRegistry());
    private final LisOutgoingFileAssembler assembler = new LisOutgoingFileAssembler(codec);

    @Test
    void assemblesCompleteFileWithContinuousSequencesAndExactCounters() throws Exception {
        byte[] file = assembler.assemble(
                "000123", "260726", 1, false, "000088",
                List.of(new LisFinancialRecord(tcr0(), tcr1())));

        assertEquals(14 * 256, file.length);
        String[] expectedCodes = {
                "90", "92", "93", "94", "95", "96", "97",
                "98", "05", "05", "99", "80", "81", "91"};
        for (int index = 0; index < expectedCodes.length; index++) {
            byte[] physical = java.util.Arrays.copyOfRange(file, index * 256, (index + 1) * 256);
            ISOMsg decoded = codec.unpack(physical);
            assertEquals(expectedCodes[index], decoded.getString(0));
            assertEquals("%06d".formatted(index + 1), decoded.getString(1));
        }

        String localTrailer = new String(file, 10 * 256, 256, StandardCharsets.US_ASCII);
        assertEquals("000004", localTrailer.substring(9, 15));
        assertEquals("000001", localTrailer.substring(51, 57));
        String fileTrailer = new String(file, 13 * 256, 256, StandardCharsets.US_ASCII);
        assertEquals("000014", fileTrailer.substring(9, 15));
        assertEquals("000001", fileTrailer.substring(51, 57));

        var parsed = new LisIncomingFileReader(codec).read(file);
        assertEquals("000088", parsed.originatorBankCode());
        assertEquals("000123", parsed.destinationBankCode());
        assertEquals(1, parsed.financialRecords().size());
    }

    @Test
    void rejectsBrokenPhysicalSequence() throws Exception {
        byte[] file = assembler.assemble("000123", "260726", 1, false, "000088", List.of());
        file[256 + 7] = '9';
        assertThrows(org.jpos.iso.ISOException.class,
                () -> new LisIncomingFileReader(codec).read(file));
    }

    @Test
    void countsChargebackAndRepresentationInNormativeTrailerFields() throws Exception {
        LisFinancialRecord original = new LisFinancialRecord(tcr0(), tcr1());
        LisDisputeRecordFactory disputes = new LisDisputeRecordFactory();
        LisFinancialRecord chargeback = disputes.create(
                original, "15", 1, "1001", "000123", 1000, "chargeback");
        LisFinancialRecord representation = disputes.create(
                original, "05", 2, "1001", "000124", 1000, "representation");
        byte[] file = assembler.assemble("000123", "260726", 2, false, "000088",
                List.of(original, chargeback, representation));

        String localTrailer = new String(file, 14 * 256, 256, StandardCharsets.US_ASCII);
        assertEquals("000001", localTrailer.substring(51, 57)); // F011 first presentation
        assertEquals("000001", localTrailer.substring(69, 75)); // F014 second presentation
        assertEquals("000001", localTrailer.substring(81, 87)); // F016 first chargeback
        String fileTrailer = new String(file, 17 * 256, 256, StandardCharsets.US_ASCII);
        assertEquals("000001", fileTrailer.substring(51, 57));
        assertEquals("000001", fileTrailer.substring(69, 75));
        assertEquals("000001", fileTrailer.substring(81, 87));
    }

    private static ISOMsg tcr0() {
        ISOMsg message = new ISOMsg();
        String[] values = {
                "05", "", "", "0", "0000000001", "MERCHANT",
                "CASABLANCA", "MAR", "5999", " ", "  ", "2", "TERM0001",
                "1", "000", "INVOICE", "0000", "000000", " ", "1",
                "4000001234567899", "2812", "2", "I", "00", "00000", ""
        };
        for (int field = 0; field < values.length; field++) message.set(field, values[field]);
        return message;
    }

    private static ISOMsg tcr1() {
        ISOMsg message = new ISOMsg();
        String[] values = {
                "05", "", "", "230726", "654321", "2", "0",
                "ACQ-REF-000000000000001", "00008800", " ", "2", "000000001000",
                "00012300", " ", "2", "000000001000", "000000001000",
                "000000000000", "230726", "230726", "   ", " ", "00", "0000",
                "000", "000000", "620414260723", "145135", "504", "504",
                "000000000000", "000000000000", " ", "000000000000",
                "000000000", "000000000", "0000000", ""
        };
        for (int field = 0; field < values.length; field++) message.set(field, values[field]);
        return message;
    }
}
