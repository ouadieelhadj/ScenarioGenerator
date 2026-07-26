package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IF_CHAR;

/** LIS 4.13 section 7.3, TC92/94/96/98/80 TCR0 logical-file header. */
public final class LisLogicalHeaderPackager extends LisFixedRecordPackager {
    public LisLogicalHeaderPackager() {
        super(
                new IFA_NUMERIC(2, "F001 TRANSACTION CODE"),
                new IFA_NUMERIC(6, "F002 FILE RECORD SEQUENCE NUMBER"),
                new IFA_NUMERIC(1, "F003 TCR SEQUENCE NUMBER"),
                new IF_CHAR(247, "F004 FILLER")
        );
    }
}
