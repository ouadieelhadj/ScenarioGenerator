package com.staging.sg.common.iso;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sanitized structural reference derived from a real Way4 POS_FEITIAN 0200.
 *
 * <p>No real PAN, track, PIN block, ICC cryptogram, MAC or key is retained.
 * The neutral values preserve the observed field presence and byte lengths so
 * future packager changes can be checked without persisting payment data.</p>
 */
class WayPosRealServerReferenceTest {
    private static final String EXPECTED_SANITIZED_0200_SHA256 =
            "FC6C6DDAE09AF368ABD31C8D4FA1C75CAA9D42B3E7DE26B1860B8EDFEDC1AE8C";

    @Test
    void packsTheObservedRealServer0200ShapeInto291Bytes() throws Exception {
        WayPosPackager packager = new WayPosPackager();
        ISOMsg source = sanitizedObserved0200(packager);

        byte[] payload = source.pack();

        assertEquals(291, payload.length);
        assertEquals(EXPECTED_SANITIZED_0200_SHA256,
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(payload))
                        .toUpperCase());

        ISOMsg decoded = new ISOMsg();
        decoded.setPackager(packager);
        decoded.unpack(payload);

        WayPosMessageValidator.validateRequest(decoded);
        assertEquals("0200", decoded.getMTI());
        assertEquals("12488881", decoded.getString(41));
        assertEquals(131, decoded.getBytes(55).length);
        assertEquals(57, decoded.getString(63).length());
        assertArrayEquals(new byte[4], decoded.getBytes(64));
    }

    private ISOMsg sanitizedObserved0200(WayPosPackager packager) throws Exception {
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI("0200");
        message.set(2, "4111111111111111");
        message.set(3, "000000");
        message.set(4, "000000006589");
        message.set(7, "0724154116");
        message.set(11, "000217");
        message.set(14, "2512");
        message.set(22, "051");
        message.set(23, "000");
        message.set(25, "00");
        message.set(35, "0".repeat(33));
        message.set(41, "12488881");
        message.set(49, "504");
        message.set(52, new byte[8]);
        byte[] neutralIccData = new byte[131];
        Arrays.fill(neutralIccData, (byte) 0x00);
        message.set(55, neutralIccData);
        message.set(63, "016SV18520747960000020RN124888811784904076012PC2100141001");
        message.set(64, new byte[4]);
        return message;
    }
}
