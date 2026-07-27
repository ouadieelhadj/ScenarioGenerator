package com.staging.sg.swam.lis.common.model;

import org.jpos.iso.ISOMsg;

/** Safe constructors for the first LIS 4.13 envelope records. */
public final class LisRecordFactory {
    private LisRecordFactory() {
    }

    public static ISOMsg fileHeader(
            int recordSequence,
            String destinationBankCode,
            String processingDateDdMmYy,
            int fileSequence,
            boolean regenerated,
            String originatorBankCode) {
        requireDigits(destinationBankCode, 6, "destination bank code");
        requireDigits(processingDateDdMmYy, 6, "processing date");
        requireDigits(originatorBankCode, 6, "originator bank code");
        if (recordSequence != 1) {
            throw new IllegalArgumentException("TC90 record sequence must be 000001");
        }
        if (fileSequence < 1 || fileSequence > 999) {
            throw new IllegalArgumentException("LIS file sequence must be between 001 and 999");
        }

        ISOMsg message = new ISOMsg();
        message.set(0, "90");
        message.set(1, "%06d".formatted(recordSequence));
        message.set(2, "0");
        message.set(3, destinationBankCode);
        message.set(4, processingDateDdMmYy);
        message.set(5, "%03d".formatted(fileSequence));
        message.set(6, regenerated ? "R" : " ");
        message.set(7, originatorBankCode);
        message.set(8, " ".repeat(225));
        return message;
    }

    public static ISOMsg logicalHeader(String transactionCode, int recordSequence) {
        if (!java.util.Set.of("92", "94", "96", "98", "80").contains(transactionCode)) {
            throw new IllegalArgumentException("Invalid LIS logical-header transaction code");
        }
        if (recordSequence < 1 || recordSequence > 999_999) {
            throw new IllegalArgumentException("Invalid LIS physical record sequence");
        }
        ISOMsg message = new ISOMsg();
        message.set(0, transactionCode);
        message.set(1, "%06d".formatted(recordSequence));
        message.set(2, "0");
        message.set(3, " ".repeat(247));
        return message;
    }

    /**
     * Creates a LIS logical-file trailer. Counter keys use specification field
     * numbers 4..41 and unspecified counters are encoded as zero.
     */
    public static ISOMsg logicalTrailer(
            String transactionCode, int recordSequence, java.util.Map<Integer, Long> counters) {
        if (!java.util.Set.of("93", "95", "97", "99", "81").contains(transactionCode)) {
            throw new IllegalArgumentException("Invalid LIS logical-trailer transaction code");
        }
        if (recordSequence < 1 || recordSequence > 999_999) {
            throw new IllegalArgumentException("Invalid LIS physical record sequence");
        }

        java.util.Map<Integer, Long> safeCounters =
                counters == null ? java.util.Map.of() : java.util.Map.copyOf(counters);
        for (Integer field : safeCounters.keySet()) {
            if (field < 4 || field > 41) {
                throw new IllegalArgumentException(
                        "Unsupported TC" + transactionCode + " counter F" + field);
            }
        }

        ISOMsg message = new ISOMsg();
        message.set(0, transactionCode);
        message.set(1, "%06d".formatted(recordSequence));
        message.set(2, "0");
        for (int specificationField = 4; specificationField <= 41; specificationField++) {
            int length = specificationField == 22 ? 2 : 6;
            long value = safeCounters.getOrDefault(specificationField, 0L);
            long maximum = length == 2 ? 99L : 999_999L;
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException(
                        "TC" + transactionCode + " F" + specificationField + " exceeds n" + length);
            }
            message.set(specificationField - 1, ("%0" + length + "d").formatted(value));
        }
        message.set(41, " ".repeat(23));
        return message;
    }

    /**
     * Creates TC91/TCR0. Counter keys use specification field numbers 5..38.
     * Unspecified counters are encoded as zero.
     */
    public static ISOMsg fileTrailer(
            int recordSequence, int totalTcrRecords, java.util.Map<Integer, Long> counters) {
        if (recordSequence < 1 || recordSequence > 999_999) {
            throw new IllegalArgumentException("Invalid LIS physical record sequence");
        }
        if (totalTcrRecords != recordSequence) {
            throw new IllegalArgumentException(
                    "TC91 F004 total TCR records must equal TC91 F002 sequence");
        }
        ISOMsg message = new ISOMsg();
        message.set(0, "91");
        message.set(1, "%06d".formatted(recordSequence));
        message.set(2, "0");
        message.set(3, "%06d".formatted(totalTcrRecords));

        java.util.Map<Integer, Long> safeCounters =
                counters == null ? java.util.Map.of() : java.util.Map.copyOf(counters);
        for (Integer field : safeCounters.keySet()) {
            if (field < 5 || field > 38) {
                throw new IllegalArgumentException("Unsupported TC91 counter F" + field);
            }
        }
        for (int specificationField = 5; specificationField <= 38; specificationField++) {
            int length = specificationField <= 19 || specificationField >= 34 ? 6 : 4;
            long value = safeCounters.getOrDefault(specificationField, 0L);
            long maximum = length == 6 ? 999_999L : 9_999L;
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException(
                        "TC91 F" + specificationField + " exceeds n" + length);
            }
            // jPOS component index is specification field number minus one.
            message.set(specificationField - 1, ("%0" + length + "d").formatted(value));
        }
        message.set(38, " ".repeat(65));
        return message;
    }

    private static void requireDigits(String value, int length, String label) {
        if (value == null || !value.matches("\\d{" + length + "}")) {
            throw new IllegalArgumentException(label + " must contain " + length + " digits");
        }
    }
}
