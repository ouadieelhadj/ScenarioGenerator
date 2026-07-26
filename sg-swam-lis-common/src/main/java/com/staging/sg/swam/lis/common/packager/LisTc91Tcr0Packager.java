package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IF_CHAR;

import java.util.ArrayList;
import java.util.List;

/** LIS 4.13 sections 7.2, TC91/TCR0 global file trailer. */
public final class LisTc91Tcr0Packager extends LisFixedRecordPackager {
    public LisTc91Tcr0Packager() {
        super(fields());
    }

    private static org.jpos.iso.ISOFieldPackager[] fields() {
        List<org.jpos.iso.ISOFieldPackager> fields = new ArrayList<>();
        fields.add(new IFA_NUMERIC(2, "F001 TRANSACTION CODE"));
        fields.add(new IFA_NUMERIC(6, "F002 FILE RECORD SEQUENCE NUMBER"));
        fields.add(new IFA_NUMERIC(1, "F003 TCR SEQUENCE NUMBER"));

        // F004..F019: global TCR/presentment/dispute counters, n6.
        for (int field = 4; field <= 19; field++) {
            fields.add(new IFA_NUMERIC(6, "F%03d GLOBAL COUNTER".formatted(field)));
        }
        // F020..F033: fee/message/report counters, n4.
        for (int field = 20; field <= 33; field++) {
            fields.add(new IFA_NUMERIC(4, "F%03d GLOBAL COUNTER".formatted(field)));
        }
        // F034..F038: FS service counters, n6.
        for (int field = 34; field <= 38; field++) {
            fields.add(new IFA_NUMERIC(6, "F%03d GLOBAL COUNTER".formatted(field)));
        }
        fields.add(new IF_CHAR(65, "F039 FILLER"));
        return fields.toArray(org.jpos.iso.ISOFieldPackager[]::new);
    }
}
