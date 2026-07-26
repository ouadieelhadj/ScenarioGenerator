package com.staging.sg.swam.acquirer.network;

import com.staging.sg.common.entity.SwamInterface;
import com.staging.sg.common.iso.SwamPackager;
import com.staging.sg.common.iso.sid.SidMessageValidator;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SwamAuthorizationSidComplianceTest {
    private SwamAuthorization builder;
    private SwamPackager packager;

    @BeforeEach
    void setUp() {
        SwamInterface config = new SwamInterface();
        config.setAcquirerCodeDe32("300853");
        config.setIssuerCodeDe33("300853");
        config.setMemberGroupId("SWAM-MEMBER");
        builder = new SwamAuthorization(() -> config);
        ReflectionTestUtils.setField(builder, "countryCode", "504");
        ReflectionTestUtils.setField(builder, "defaultExpiry", "2712");
        ReflectionTestUtils.setField(builder, "defaultMcc", "5411");
        ReflectionTestUtils.setField(builder, "terminalId", "TERM0001");
        ReflectionTestUtils.setField(builder, "merchantId", "MERCHANT000001");
        ReflectionTestUtils.setField(builder, "merchantNameLocation", "MONEYCORE CASABLANCA MA");
        packager = new SwamPackager();
    }

    @Test
    void authorization1100IsSidCompliantAndNotClearingEligibleByItself() throws Exception {
        ISOMsg message = builder.buildAuth1100(
                "5321962145453348", "000000010000", "000001", packager);

        SidMessageValidator.validate(withDummyMac(message));
        assertEquals("1100", message.getMTI());
        assertEquals("100", message.getString(24));
        assertFalse(message.hasField(5), "authorization must not masquerade as financial capture");
        assertRoundTrip(message);
    }

    @Test
    void financial1200IsSidCompliant() throws Exception {
        ISOMsg message = builder.buildFinancial1200(
                "5321962145453348", "000000010000", "000002", packager);

        SidMessageValidator.validate(withDummyMac(message));
        assertEquals("200", message.getString(24));
        assertEquals("000000010000", message.getString(5));
        assertEquals("504", message.getString(50));
        assertRoundTrip(message);
    }

    @Test
    void financialAdvice1220IsSidCompliant() throws Exception {
        ISOMsg message = builder.buildFinancialAdvice1220(
                "5321962145453348", "000000010000", "000003",
                "123456", "11000000012607261200000001", packager);

        SidMessageValidator.validate(withDummyMac(message));
        assertEquals("201", message.getString(24));
        assertEquals("123456", message.getString(38));
        assertRoundTrip(message);
    }

    @Test
    void totalReversal1420IsSidCompliant() throws Exception {
        ISOMsg message = builder.buildReversal1420(
                "5321962145453348", "000000010000", "000004",
                "000002000000", "123456", "12000000022607261200000002",
                false, null, packager);

        SidMessageValidator.validate(withDummyMac(message));
        assertEquals("400", message.getString(24));
        assertFalse(message.hasField(30));
        assertRoundTrip(message);
    }

    private ISOMsg withDummyMac(ISOMsg message) throws Exception {
        message.set(128, new byte[] {0,0,0,0});
        return message;
    }

    private void assertRoundTrip(ISOMsg original) throws Exception {
        byte[] bytes = original.pack();
        ISOMsg decoded = new ISOMsg();
        decoded.setPackager(packager);
        decoded.unpack(bytes);
        assertArrayEquals(bytes, decoded.pack());
    }
}
