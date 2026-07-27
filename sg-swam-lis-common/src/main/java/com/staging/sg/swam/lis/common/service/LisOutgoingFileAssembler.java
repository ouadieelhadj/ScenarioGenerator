package com.staging.sg.swam.lis.common.service;

import com.staging.sg.swam.lis.common.codec.LisRecordCodec;
import com.staging.sg.swam.lis.common.model.LisFinancialRecord;
import com.staging.sg.swam.lis.common.model.LisRecordFactory;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a complete local-interchange LIS 4.13 file.
 *
 * <p>All five logical sections are emitted, including empty sections. Financial
 * transactions are carried by the local section TC98/TC99. Physical sequence
 * numbers are assigned here and are continuous from TC90 through TC91.</p>
 */
public final class LisOutgoingFileAssembler {
    private static final List<String[]> SECTIONS = List.of(
            new String[] {"92", "93"},
            new String[] {"94", "95"},
            new String[] {"96", "97"},
            new String[] {"98", "99"},
            new String[] {"80", "81"});

    private final LisRecordCodec codec;

    public LisOutgoingFileAssembler(LisRecordCodec codec) {
        this.codec = codec;
    }

    public byte[] assemble(
            String destinationBankCode,
            String processingDateDdMmYy,
            int fileSequence,
            boolean regenerated,
            String originatorBankCode,
            List<LisFinancialRecord> localRecords) throws ISOException {
        List<LisFinancialRecord> financialRecords =
                localRecords == null ? List.of() : List.copyOf(localRecords);
        List<ISOMsg> records = new ArrayList<>();
        int sequence = 1;
        records.add(LisRecordFactory.fileHeader(sequence++, destinationBankCode,
                processingDateDdMmYy, fileSequence, regenerated, originatorBankCode));

        for (String[] section : SECTIONS) {
            records.add(LisRecordFactory.logicalHeader(section[0], sequence++));
            int logicalPhysicalRecords = 2;
            java.util.Map<Integer, Long> logicalCounters = new java.util.HashMap<>();
            if ("98".equals(section[0])) {
                for (LisFinancialRecord financialRecord : financialRecords) {
                    assignSequence(financialRecord.tcr0(), sequence++, 0);
                    assignSequence(financialRecord.tcr1(), sequence++, 1);
                    records.add(financialRecord.tcr0());
                    records.add(financialRecord.tcr1());
                    logicalPhysicalRecords += 2;
                    int counter = logicalCounter(financialRecord);
                    logicalCounters.merge(counter, 1L, Long::sum);
                }
            }
            logicalCounters.put(4, (long) logicalPhysicalRecords);
            records.add(LisRecordFactory.logicalTrailer(section[1], sequence++, logicalCounters));
        }

        int finalSequence = sequence;
        java.util.Map<Integer, Long> fileCounters = new java.util.HashMap<>();
        for (LisFinancialRecord record : financialRecords) {
            fileCounters.merge(logicalCounter(record), 1L, Long::sum);
        }
        records.add(LisRecordFactory.fileTrailer(finalSequence, finalSequence, fileCounters));

        ByteArrayOutputStream output = new ByteArrayOutputStream(records.size() * 256);
        for (ISOMsg record : records) {
            output.writeBytes(codec.pack(record));
        }
        return output.toByteArray();
    }

    private static void assignSequence(ISOMsg record, int sequence, int tcr) {
        record.set(1, "%06d".formatted(sequence));
        record.set(2, Integer.toString(tcr));
    }

    private static int logicalCounter(LisFinancialRecord record) {
        String code = record.tcr0().getString(0);
        boolean second = "2".equals(record.tcr0().getString(13));
        if (java.util.Set.of("15", "16", "17").contains(code)) return second ? 18 : 16;
        if (java.util.Set.of("25", "26", "27").contains(code)) return second ? 15 : 13;
        if (java.util.Set.of("35", "36", "37").contains(code)) return second ? 19 : 17;
        return second ? 14 : 11;
    }
}
