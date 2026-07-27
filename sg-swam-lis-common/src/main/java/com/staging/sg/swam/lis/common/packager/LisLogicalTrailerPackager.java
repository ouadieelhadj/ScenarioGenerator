package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IF_CHAR;
import org.jpos.iso.ISOFieldPackager;

/**
 * LIS 4.13 section 7.4, TC93/95/97/99/81 TCR0 logical-file trailer.
 *
 * <p>The published table declares F022 as n6, but its positions put F022 at
 * 118 and F023 at 120. Encoding F022 as n2 is the only interpretation that
 * preserves every subsequent published position, the filler at 234 and the
 * mandatory 256-byte physical record length.</p>
 */
public final class LisLogicalTrailerPackager extends LisFixedRecordPackager {
    public LisLogicalTrailerPackager() {
        super(fields());
    }

    private static ISOFieldPackager[] fields() {
        ISOFieldPackager[] fields = new ISOFieldPackager[42];
        fields[0] = new IFA_NUMERIC(2, "F001 TRANSACTION CODE");
        fields[1] = new IFA_NUMERIC(6, "F002 FILE RECORD SEQUENCE NUMBER");
        fields[2] = new IFA_NUMERIC(1, "F003 TCR SEQUENCE NUMBER");
        for (int index = 3; index <= 20; index++) {
            fields[index] = new IFA_NUMERIC(6, counterName(index + 1));
        }
        fields[21] = new IFA_NUMERIC(2, counterName(22));
        for (int index = 22; index <= 40; index++) {
            fields[index] = new IFA_NUMERIC(6, counterName(index + 1));
        }
        fields[41] = new IF_CHAR(23, "F042 FILLER");
        return fields;
    }

    private static String counterName(int specificationField) {
        return "F%03d LOGICAL FILE COUNTER".formatted(specificationField);
    }
}
