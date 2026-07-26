package com.staging.sg.swam.lis.common.packager;

public record LisRecordKey(String transactionCode, int tcrSequence) {
    public LisRecordKey {
        if (transactionCode == null || !transactionCode.matches("\\d{2}")) {
            throw new IllegalArgumentException("LIS transaction code must contain two digits");
        }
        if (tcrSequence < 0 || tcrSequence > 9) {
            throw new IllegalArgumentException("LIS TCR sequence must be between 0 and 9");
        }
    }
}
