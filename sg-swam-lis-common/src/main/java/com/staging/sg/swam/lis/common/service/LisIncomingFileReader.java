package com.staging.sg.swam.lis.common.service;

import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.LisFinancialRecord;
import com.staging.sg.swam.lis.common.model.ParsedLisFile;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Strict structural validation and decoding of one complete LIS 4.13 file. */
public final class LisIncomingFileReader {
    private static final Set<String> FINANCIAL = Set.of(
            "05", "06", "07", "15", "16", "17", "25", "26", "27", "35", "36", "37");
    private final LisRecordCodec codec;

    public LisIncomingFileReader(LisRecordCodec codec) {
        this.codec = codec;
    }

    public ParsedLisFile read(byte[] file) throws ISOException {
        if (file == null || file.length == 0 || file.length % 256 != 0) {
            throw new ISOException("LIS file length must be a positive multiple of 256");
        }
        int count = file.length / 256;
        List<ISOMsg> records = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ISOMsg record = codec.unpack(Arrays.copyOfRange(file, index * 256, (index + 1) * 256));
            int sequence = Integer.parseInt(record.getString(1));
            if (sequence != index + 1) {
                throw new ISOException("Non-contiguous LIS sequence at physical record " + (index + 1));
            }
            records.add(record);
        }
        ISOMsg header = records.getFirst();
        ISOMsg trailer = records.getLast();
        if (!"90".equals(header.getString(0)) || !"91".equals(trailer.getString(0))) {
            throw new ISOException("LIS file must start with TC90 and end with TC91");
        }
        if (Integer.parseInt(trailer.getString(3)) != count) {
            throw new ISOException("TC91 physical record count differs from actual file");
        }
        validateMandatorySections(records);

        List<LisFinancialRecord> financial = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            ISOMsg candidate = records.get(index);
            if (FINANCIAL.contains(candidate.getString(0)) && "0".equals(candidate.getString(2))) {
                if (index + 1 >= records.size()) throw new ISOException("Financial TCR0 without TCR1");
                ISOMsg tcr1 = records.get(++index);
                if (!candidate.getString(0).equals(tcr1.getString(0))
                        || !"1".equals(tcr1.getString(2))) {
                    throw new ISOException("Invalid financial TCR0/TCR1 pair");
                }
                financial.add(new LisFinancialRecord(candidate, tcr1));
            }
        }
        return new ParsedLisFile(header.getString(7), header.getString(3),
                header.getString(4), Integer.parseInt(header.getString(5)),
                count, List.copyOf(financial));
    }

    private static void validateMandatorySections(List<ISOMsg> records) throws ISOException {
        String[] expected = {"92", "93", "94", "95", "96", "97", "98", "99", "80", "81"};
        int cursor = 1;
        for (int pair = 0; pair < expected.length; pair += 2) {
            if (cursor >= records.size() || !expected[pair].equals(records.get(cursor).getString(0))) {
                throw new ISOException("Missing logical header TC" + expected[pair]);
            }
            cursor++;
            while (cursor < records.size() && !expected[pair + 1].equals(records.get(cursor).getString(0))) {
                cursor++;
            }
            if (cursor >= records.size()) throw new ISOException("Missing logical trailer TC" + expected[pair + 1]);
            cursor++;
        }
        if (cursor != records.size() - 1) throw new ISOException("Unexpected record outside logical sections");
    }
}
