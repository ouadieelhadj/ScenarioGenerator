package com.staging.sg.visa.base2.common;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class VisaBase2FileCodec {
    public byte[] pack(List<VisaBase2Record> records) {
        if (records == null || records.isEmpty()) throw new IllegalArgumentException("Empty Base II file");
        ByteArrayOutputStream out = new ByteArrayOutputStream(records.size() * VisaBase2Record.LENGTH);
        records.forEach(record -> out.writeBytes(record.pack()));
        return out.toByteArray();
    }

    public List<VisaBase2Record> unpack(byte[] file) {
        if (file == null || file.length == 0 || file.length % VisaBase2Record.LENGTH != 0)
            throw new IllegalArgumentException("Base II CTF size is not a multiple of 168");
        List<VisaBase2Record> records = new ArrayList<>();
        for (int offset = 0; offset < file.length; offset += VisaBase2Record.LENGTH) {
            records.add(VisaBase2Record.unpack(java.util.Arrays.copyOfRange(file, offset,
                    offset + VisaBase2Record.LENGTH)));
        }
        return List.copyOf(records);
    }
}
