package com.staging.sg.common.iso;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WayPosMessageValidatorTest {
    @Test
    void acceptsBasicFinancialRequestWithSoftwareVersion() throws Exception {
        ISOMsg message = financial();
        assertDoesNotThrow(() -> WayPosMessageValidator.validateRequest(message));
    }

    @Test
    void rejectsMissingSoftwareVersion() throws Exception {
        ISOMsg message = financial();
        message.set(63, "006RN1234");
        assertThrows(Exception.class, () -> WayPosMessageValidator.validateRequest(message));
    }

    @Test
    void acceptsFileUpdateWithoutProcessingCodeButRequiresDe47() throws Exception {
        ISOMsg message = new ISOMsg();
        message.setMTI("0302");
        message.set(7, "0730100000");
        message.set(11, "123456");
        message.set(41, "TERM0001");
        message.set(47, new byte[] {0x01, 0x02});
        message.set(63, WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"))));

        assertDoesNotThrow(() -> WayPosMessageValidator.validateRequest(message));
        message.unset(47);
        assertThrows(ISOException.class,
                () -> WayPosMessageValidator.validateRequest(message));
    }

    @Test
    void validatesCardControlInquiryType() throws Exception {
        ISOMsg message = financial();
        message.setMTI("0100");
        message.set(3, "910000");
        message.set(63, WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("62", "24"))));
        assertDoesNotThrow(() -> WayPosMessageValidator.validateRequest(message));

        message.set(63, WayPosPrivateData.encode(List.of(
                new WayPosPrivateData.Item("SV", "1.0.0"),
                new WayPosPrivateData.Item("62", "99"))));
        assertThrows(ISOException.class,
                () -> WayPosMessageValidator.validateRequest(message));
    }

    private static ISOMsg financial() throws Exception {
        ISOMsg m = new ISOMsg();
        m.setPackager(new WayPosPackager());
        m.setMTI("0200");
        m.set(2, "4123456789012345");
        m.set(3, "000000");
        m.set(4, "000000001000");
        m.set(7, "0729123456");
        m.set(11, "123456");
        m.set(14, "2912");
        m.set(22, "051");
        m.set(25, "00");
        m.set(37, "123456789012");
        m.set(41, "TERM0001");
        m.set(49, "504");
        m.set(63, "007SV1.0.0");
        return m;
    }
}
