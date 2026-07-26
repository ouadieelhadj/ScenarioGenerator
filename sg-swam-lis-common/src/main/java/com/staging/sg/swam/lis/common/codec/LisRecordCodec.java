package com.staging.sg.swam.lis.common.codec;

import com.staging.sg.swam.lis.common.packager.LisFixedRecordPackager;
import com.staging.sg.swam.lis.common.packager.LisPackagerRegistry;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Packs and unpacks one LIS physical record using its registered jPOS packager. */
public final class LisRecordCodec {
    private final LisPackagerRegistry registry;

    public LisRecordCodec(LisPackagerRegistry registry) {
        this.registry = registry;
    }

    public byte[] pack(ISOMsg message) throws ISOException {
        String transactionCode = message.getString(0);
        int tcr = Integer.parseInt(message.getString(2));
        ISOPackager packager = registry.require(transactionCode, tcr);
        message.setPackager(packager);
        byte[] packed = message.pack();
        validatePhysicalRecord(packed);
        assertKey(packed, transactionCode, tcr);
        return packed;
    }

    public ISOMsg unpack(byte[] record) throws ISOException {
        validatePhysicalRecord(record);
        String transactionCode = ascii(record, 0, 2);
        int tcr = Character.digit((char) record[8], 10);
        if (tcr < 0) throw new ISOException("Invalid LIS TCR sequence at position 9");

        ISOMsg message = new ISOMsg();
        message.setPackager(registry.require(transactionCode, tcr));
        int consumed = message.unpack(record);
        if (consumed != LisFixedRecordPackager.RECORD_LENGTH) {
            throw new ISOException("LIS packager consumed " + consumed + " bytes instead of 256");
        }
        return message;
    }

    private static void validatePhysicalRecord(byte[] record) throws ISOException {
        if (record == null || record.length != LisFixedRecordPackager.RECORD_LENGTH) {
            throw new ISOException("A LIS physical record must contain exactly 256 bytes");
        }
        try {
            StandardCharsets.US_ASCII.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(record));
        } catch (CharacterCodingException e) {
            throw new ISOException("LIS record is not strict US-ASCII", e);
        }
    }

    private static void assertKey(byte[] record, String code, int tcr) throws ISOException {
        if (!code.equals(ascii(record, 0, 2)) || record[8] != (byte) ('0' + tcr)) {
            throw new ISOException("Packed LIS record key differs from requested TC/TCR");
        }
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return StandardCharsets.US_ASCII.decode(
                ByteBuffer.wrap(bytes, offset, length)).toString();
    }
}
