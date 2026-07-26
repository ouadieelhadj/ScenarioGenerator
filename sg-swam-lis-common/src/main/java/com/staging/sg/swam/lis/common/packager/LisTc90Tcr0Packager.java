package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IF_CHAR;

/** LIS 4.13 section 7.1, TC90/TCR0 file header. */
public final class LisTc90Tcr0Packager extends LisFixedRecordPackager {
    public LisTc90Tcr0Packager() {
        super(
                new IFA_NUMERIC(2, "F001 TRANSACTION CODE"),
                new IFA_NUMERIC(6, "F002 FILE RECORD SEQUENCE NUMBER"),
                new IFA_NUMERIC(1, "F003 TCR SEQUENCE NUMBER"),
                new IFA_NUMERIC(6, "F004 DESTINATION BANK CODE"),
                new IFA_NUMERIC(6, "F005 FILE PROCESSING DATE"),
                new IFA_NUMERIC(3, "F006 FILE SEQUENCE NUMBER"),
                new IF_CHAR(1, "F007 FILE STATUS INDICATOR"),
                new IFA_NUMERIC(6, "F008 ORIGINATOR BANK"),
                new IF_CHAR(225, "F009 FILLER")
        );
    }
}
