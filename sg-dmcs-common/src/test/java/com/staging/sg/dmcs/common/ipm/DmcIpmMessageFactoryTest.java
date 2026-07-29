package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DmcIpmMessageFactoryTest {

    private final DmcIpmPackager packager = new DmcIpmPackager();
    private final DmcIpmMessageFactory factory = new DmcIpmMessageFactory(packager);

    @Test
    void buildsNormativeHeaderPresentmentTrailerAndRoundTripsRdw() throws Exception {
        var parameters = new DmcIpmMessageFactory.FileParameters(
                "002", LocalDate.of(2026, 7, 28), "22905", 7,
                "T", "555555", "22905");
        var presentment = new DmcIpmMessageFactory.PresentmentData(
                "5413330089012345", "000000", 12_345,
                "260728153000", "2906", "901900C99000", "5999",
                "12345678901234567890123", "22905", null,
                "620928123456", "ABC123", "TERM0001", "MERCHANT000001",
                "TEST MERCHANT CASABLANCA MA", "504",
                "555555", "22905", null);

        var built = factory.build(parameters, List.of(presentment));

        assertEquals("0022607280000002290500007", built.fileId());
        assertEquals(12_345, built.amountChecksum());
        assertEquals(3, built.messages().size());
        assertMessage(built.messages().get(0), "1644", "697", "00000001");
        assertMessage(built.messages().get(1), "1240", "200", "00000002");
        assertMessage(built.messages().get(2), "1644", "695", "00000003");

        var trailerPds = DmcPdsCodec.decode(built.messages().get(2).getString(48));
        assertEquals("0000000000012345", trailerPds.get(301));
        assertEquals("00000003", trailerPds.get(306));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new DmcIpmFileCodec(packager).write(output, built.messages());
        List<ISOMsg> decoded = new DmcIpmFileCodec(packager)
                .read(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(3, decoded.size());
        assertEquals("12345678901234567890123", decoded.get(1).getString(31));
        var validation = DmcIpmFileValidator.validate(decoded);
        assertEquals(built.fileId(), validation.fileId());
        assertEquals(3, validation.messageCount());
        assertEquals(12_345, validation.amountChecksum());
    }

    @Test
    void rejectsBrokenDe71Sequence() throws Exception {
        var parameters = new DmcIpmMessageFactory.FileParameters(
                "002", LocalDate.of(2026, 7, 28), "22905", 1,
                "T", null, null);
        var built = factory.build(parameters, List.of());
        built.messages().get(1).set(71, "00000009");

        assertThrows(IllegalArgumentException.class,
                () -> DmcIpmFileValidator.validate(built.messages()));
    }

    @Test
    void refusesFirstPresentmentWithoutMandatoryDe31() {
        var parameters = new DmcIpmMessageFactory.FileParameters(
                "002", LocalDate.of(2026, 7, 28), "22905", 1,
                "T", null, null);
        var presentment = new DmcIpmMessageFactory.PresentmentData(
                "5413330089012345", "000000", 100,
                "260728153000", null, "901900C99000", "5999",
                null, null, null, null, null, null, null, null,
                "504", null, null, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> factory.build(parameters, List.of(presentment)));
        assertEquals("DE31 doit etre numerique sur 23..23 positions", error.getMessage());
    }

    @Test
    void buildsCompleteChargebackFileWithHeaderAndTrailer() throws Exception {
        var parameters = new DmcIpmMessageFactory.FileParameters(
                "002", LocalDate.of(2026, 7, 29), "22905", 8,
                "T", "555555", "22905");
        String pds = DmcPdsCodec.concat(
                DmcPdsCodec.encode(148, "0202"),
                DmcPdsCodec.encode(149, "50425042"));
        var dispute = new DmcDisputeMessageFactory.DisputeData(
                "450", "5413330089012345", "000000", 10_000,
                "260729153000", "2906", "M01101C99000", "4808",
                "5999", "000000010000000000010000",
                "12345678901234567890123", "22905", "555555",
                "620928123456", "ABC123", "TERM0001",
                "MERCHANT0000001", "TEST MERCHANT CASABLANCA MA",
                "504", "555555", "22905",
                "ISSUER-REFERENCE-DATA-12345678901234567890", pds);

        var built = factory.buildDisputes(parameters, List.of(dispute));

        assertEquals(3, built.messages().size());
        assertMessage(built.messages().get(0), "1644", "697", "00000001");
        assertMessage(built.messages().get(1), "1442", "450", "00000002");
        assertMessage(built.messages().get(2), "1644", "695", "00000003");
        assertEquals(10_000, built.amountChecksum());
        assertEquals(10_000,
                DmcIpmFileValidator.validate(built.messages()).amountChecksum());
    }

    private static void assertMessage(ISOMsg message, String mti, String function, String sequence)
            throws Exception {
        assertEquals(mti, message.getMTI());
        assertEquals(function, message.getString(24));
        assertEquals(sequence, message.getString(71));
    }
}
