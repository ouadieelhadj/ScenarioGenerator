package com.staging.sg.common.iso.sid;

import com.staging.sg.common.iso.SwamPackager;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidMessageValidatorTest {

    @Test
    void rejectsIncompleteAuthorization() throws Exception {
        ISOMsg message = message("1100");
        message.set(2, "5321962145453348");

        SidValidationException exception = assertThrows(
                SidValidationException.class,
                () -> SidMessageValidator.validate(message));

        assertTrue(exception.getViolations().stream().anyMatch(v -> v.contains("DE3")));
        assertTrue(exception.getViolations().stream().anyMatch(v -> v.contains("DE124")));
    }

    @Test
    void acceptsStructurallyCompleteAuthorization() throws Exception {
        ISOMsg message = complete1100();
        assertDoesNotThrow(() -> SidMessageValidator.validate(message));
    }

    @Test
    void rejectsPartialReversalWithoutOriginalAmounts() throws Exception {
        ISOMsg message = complete1420();
        message.set(24, "402");

        SidValidationException exception = assertThrows(
                SidValidationException.class,
                () -> SidMessageValidator.validate(message));

        assertTrue(exception.getViolations().stream().anyMatch(v -> v.contains("DE30")));
    }

    @Test
    void packagerRoundTripSupportsSenderIdentificationDe124() throws Exception {
        ISOMsg original = complete1100();
        byte[] packed = original.pack();

        ISOMsg unpacked = message(null);
        unpacked.unpack(packed);

        assertTrue(unpacked.hasField(124));
        assertTrue(unpacked.getString(124).equals("SWAM-MEMBER"));
        assertTrue(java.util.Arrays.equals(packed, unpacked.pack()));
    }

    private static ISOMsg complete1100() throws Exception {
        ISOMsg m = message("1100");
        set(m, 2,"5321962145453348", 3,"000000", 4,"000000010000",
                6,"000000010000", 7,"2607261200", 10,"71000000",
                11,"000001", 12,"260726120000", 14,"2712", 15,"260726",
                16,"0726", 18,"5411", 19,"504", 21,"504",
                22,"P10101511004", 24,"100", 32,"300853", 33,"300853",
                37,"000001000000", 41,"TERM0001", 42,"MERCHANT000001 ",
                43,"MERCHANT CASABLANCA MA", 49,"504", 51,"504",
                53,"0099000000", 61,"039003000", 124,"SWAM-MEMBER");
        m.set(128, new byte[] {0,0,0,0});
        return m;
    }

    private static ISOMsg complete1420() throws Exception {
        ISOMsg m = message("1420");
        set(m, 2,"5321962145453348", 3,"000000", 4,"000000010000",
                6,"000000010000", 7,"2607261201", 10,"71000000",
                11,"000002", 12,"260726120100", 15,"260726", 16,"0726",
                19,"504", 21,"504", 24,"400", 25,"4000", 32,"300853",
                33,"300853", 37,"000001000000", 39,"000", 41,"TERM0001",
                42,"MERCHANT000001 ", 43,"MERCHANT CASABLANCA MA",
                49,"504", 50,"504", 51,"504", 53,"0099000000",
                56,"11000000010000012607261200", 124,"SWAM-MEMBER");
        m.set(128, new byte[] {0,0,0,0});
        return m;
    }

    private static ISOMsg message(String mti) throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(new SwamPackager());
        if (mti != null) m.setMTI(mti);
        return m;
    }

    private static void set(ISOMsg message, Object... fields) throws Exception {
        for (int i = 0; i < fields.length; i += 2) {
            message.set((Integer) fields[i], (String) fields[i + 1]);
        }
    }
}
