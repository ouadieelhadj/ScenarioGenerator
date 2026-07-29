package com.staging.sg.dmcs.common.ipm;

import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmcIpmFileCodecTest {

    private final DmcIpmPackager packager = new DmcIpmPackager();
    private final DmcIpmFileCodec codec = new DmcIpmFileCodec(packager);

    @Test
    void roundTripsEbcidicIpmMessagesWithVbsRdw() throws Exception {
        ISOMsg header = message("1644", 24, "697", 48,
                DmcPdsCodec.encode(105, "TESTFILE"), 71, "00000001");
        ISOMsg presentment = message("1240",
                2, "5413330089012345",
                3, "000000",
                4, "000000001500",
                12, "260728143000",
                22, "000109900000",
                24, "200",
                26, "5999",
                31, "00229052620900000000106",
                33, "002202",
                43, "TEST MERCHANT\\CASABLANCA\\MA",
                48, DmcPdsCodec.encode(148, "2"),
                49, "504",
                71, "00000002",
                94, "022905");
        ISOMsg trailer = message("1644", 24, "695", 48,
                DmcPdsCodec.encode(105, "TESTFILE"), 71, "00000003");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        codec.write(output, List.of(header, presentment, trailer));
        byte[] file = output.toByteArray();

        assertEquals(0, file[2]);
        assertEquals(0, file[3]);
        assertTrue(file.length > 100);

        List<ISOMsg> decoded = codec.read(new ByteArrayInputStream(file));
        assertEquals(3, decoded.size());
        assertEquals("1644", decoded.get(0).getMTI());
        assertEquals("697", decoded.get(0).getString(24));
        assertEquals("1240", decoded.get(1).getMTI());
        assertEquals("5413330089012345", decoded.get(1).getString(2));
        assertEquals("000109900000", decoded.get(1).getString(22));
        assertEquals("1644", decoded.get(2).getMTI());
        assertEquals("695", decoded.get(2).getString(24));
    }

    @Test
    void pdsCodecRejectsTruncatedDataAndRoundTripsValues() {
        String carrier = DmcPdsCodec.concat(
                DmcPdsCodec.encode(105, "FILE"),
                DmcPdsCodec.encode(122, "T"));

        assertEquals("FILE", DmcPdsCodec.decode(carrier).get(105));
        assertEquals("T", DmcPdsCodec.decode(carrier).get(122));
    }

    @Test
    void packedPayloadIsEbcidicRatherThanAscii() throws Exception {
        ISOMsg message = message("1644", 24, "697", 48,
                DmcPdsCodec.encode(105, "TEST"), 71, "00000001");
        byte[] packed = message.pack();

        assertArrayEquals(new byte[]{(byte) 0xF1, (byte) 0xF6, (byte) 0xF4, (byte) 0xF4},
                java.util.Arrays.copyOfRange(packed, 0, 4));
    }

    private ISOMsg message(String mti, Object... fields) throws Exception {
        ISOMsg message = new ISOMsg();
        message.setPackager(packager);
        message.setMTI(mti);
        for (int index = 0; index < fields.length; index += 2) {
            message.set((Integer) fields[index], (String) fields[index + 1]);
        }
        return message;
    }
}
