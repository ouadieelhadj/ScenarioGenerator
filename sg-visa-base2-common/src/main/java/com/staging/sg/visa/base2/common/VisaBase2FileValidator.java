package com.staging.sg.visa.base2.common;

import java.util.ArrayList;
import java.util.List;

public final class VisaBase2FileValidator {
    public ValidationResult validate(byte[] file) {
        List<String> errors = new ArrayList<>();
        List<VisaBase2Record> records;
        try { records = new VisaBase2FileCodec().unpack(file); }
        catch (RuntimeException e) { return new ValidationResult(false, 0, List.of(e.getMessage())); }
        if (!records.get(0).transactionCode().equals("90")) errors.add("TC90 header is required");
        if (records.size() < 3 || !records.get(records.size() - 2).transactionCode().equals("91"))
            errors.add("TC91 batch trailer is required");
        if (!records.get(records.size() - 1).transactionCode().equals("92")) errors.add("TC92 file trailer is required");
        int previousTcr = -1;
        String previousTc = "";
        for (VisaBase2Record record : records) {
            if (record.transactionCode().equals("05")) {
                if (!previousTc.equals("05")) previousTcr = -1;
                if (record.tcr() <= previousTcr) errors.add("TC05 TCR sequence is not increasing");
                previousTcr = record.tcr();
            }
            previousTc = record.transactionCode();
        }
        return new ValidationResult(errors.isEmpty(), records.size(), List.copyOf(errors));
    }

    public record ValidationResult(boolean valid, int recordCount, List<String> errors) {}
}
