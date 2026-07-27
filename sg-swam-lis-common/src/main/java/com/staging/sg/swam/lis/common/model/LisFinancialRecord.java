package com.staging.sg.swam.lis.common.model;

import org.jpos.iso.ISOMsg;

/** One financial LIS transaction, represented by its mandatory TCR0/TCR1 pair. */
public record LisFinancialRecord(ISOMsg tcr0, ISOMsg tcr1) {
    public LisFinancialRecord {
        if (tcr0 == null || tcr1 == null) {
            throw new IllegalArgumentException("A LIS financial record requires TCR0 and TCR1");
        }
        String transactionCode = tcr0.getString(0);
        if (!java.util.Set.of("05", "06", "07", "15", "16", "17",
                "25", "26", "27", "35", "36", "37").contains(transactionCode)) {
            throw new IllegalArgumentException("Unsupported financial transaction code " + transactionCode);
        }
        if (!transactionCode.equals(tcr1.getString(0))) {
            throw new IllegalArgumentException("TCR0 and TCR1 transaction codes differ");
        }
    }
}
