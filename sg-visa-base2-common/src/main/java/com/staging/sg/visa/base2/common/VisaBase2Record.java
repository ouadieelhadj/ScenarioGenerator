package com.staging.sg.visa.base2.common;

import java.nio.charset.Charset;
import java.util.Arrays;

public final class VisaBase2Record {
    public static final int LENGTH = 168;
    public static final Charset EBCDIC = Charset.forName("Cp1047");
    private final char[] value;

    private VisaBase2Record(char[] value) { this.value = value; }

    public static VisaBase2Record create(String transactionCode, int qualifier, int tcr) {
        char[] chars = new char[LENGTH];
        Arrays.fill(chars, ' ');
        VisaBase2Record record = new VisaBase2Record(chars);
        record.setNumeric(1, 2, transactionCode);
        record.setNumeric(3, 3, Integer.toString(qualifier));
        record.setNumeric(4, 4, Integer.toString(tcr));
        return record;
    }

    public static VisaBase2Record unpack(byte[] bytes) {
        if (bytes == null || bytes.length != LENGTH) throw new IllegalArgumentException("Base II TCR must be 168 bytes");
        String decoded = new String(bytes, EBCDIC);
        if (decoded.length() != LENGTH) throw new IllegalArgumentException("Invalid Base II EBCDIC record");
        return new VisaBase2Record(decoded.toCharArray());
    }

    public VisaBase2Record setNumeric(int start, int end, String field) {
        requireRange(start, end);
        String source = field == null ? "" : field;
        if (!source.matches("\\d*")) throw new IllegalArgumentException("Base II numeric field contains non-digits");
        int length = end - start + 1;
        if (source.length() > length) throw new IllegalArgumentException("Base II numeric field too long");
        return write(start, end, "0".repeat(length - source.length()) + source);
    }

    public VisaBase2Record setAlpha(int start, int end, String field) {
        requireRange(start, end);
        String source = field == null ? "" : field.toUpperCase(java.util.Locale.ROOT);
        int length = end - start + 1;
        if (source.length() > length) throw new IllegalArgumentException("Base II alphanumeric field too long");
        if (!source.matches("[ A-Z0-9+._:/-]*")) throw new IllegalArgumentException("Unsupported Base II character");
        return write(start, end, source + " ".repeat(length - source.length()));
    }

    public String field(int start, int end) {
        requireRange(start, end);
        return new String(value, start - 1, end - start + 1);
    }

    public String transactionCode() { return field(1, 2); }
    public int tcr() { return Integer.parseInt(field(4, 4)); }
    public byte[] pack() { return new String(value).getBytes(EBCDIC); }

    private VisaBase2Record write(int start, int end, String field) {
        for (int i = 0; i < field.length(); i++) value[start - 1 + i] = field.charAt(i);
        return this;
    }

    private static void requireRange(int start, int end) {
        if (start < 1 || end < start || end > LENGTH) throw new IllegalArgumentException("Invalid Base II field range");
    }
}
