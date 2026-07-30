package com.staging.sg.common.iso;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Minimal BER-TLV codec for OpenWay DE48/DE59 dynamic key data. */
public final class WayPosBerTlv {
    public record Tlv(int tag, byte[] value) {
        public Tlv {
            value = value.clone();
        }
        @Override public byte[] value() { return value.clone(); }
    }

    private WayPosBerTlv() {}

    public static byte[] encode(List<Tlv> values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Tlv value : values) {
            writeTag(out, value.tag());
            writeLength(out, value.value().length);
            out.writeBytes(value.value());
        }
        return out.toByteArray();
    }

    public static List<Tlv> decode(byte[] input) {
        List<Tlv> values = new ArrayList<>();
        int offset = 0;
        while (offset < input.length) {
            int first = input[offset++] & 0xFF;
            int tag = first;
            if ((first & 0x1F) == 0x1F) {
                if (offset >= input.length) throw invalid("truncated tag");
                tag = (first << 8) | (input[offset++] & 0xFF);
            }
            if (offset >= input.length) throw invalid("missing length");
            int lengthByte = input[offset++] & 0xFF;
            int length;
            if ((lengthByte & 0x80) == 0) {
                length = lengthByte;
            } else {
                int count = lengthByte & 0x7F;
                if (count == 0 || count > 2 || offset + count > input.length) {
                    throw invalid("invalid long length");
                }
                length = 0;
                for (int i = 0; i < count; i++) length = (length << 8) | (input[offset++] & 0xFF);
            }
            if (length < 0 || offset + length > input.length) throw invalid("truncated value");
            values.add(new Tlv(tag, Arrays.copyOfRange(input, offset, offset + length)));
            offset += length;
        }
        return List.copyOf(values);
    }

    private static void writeTag(ByteArrayOutputStream out, int tag) {
        if (tag > 0xFF) out.write((tag >>> 8) & 0xFF);
        out.write(tag & 0xFF);
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81); out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82); out.write(length >>> 8); out.write(length);
        } else {
            throw invalid("value too large");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid BER-TLV: " + message);
    }
}
