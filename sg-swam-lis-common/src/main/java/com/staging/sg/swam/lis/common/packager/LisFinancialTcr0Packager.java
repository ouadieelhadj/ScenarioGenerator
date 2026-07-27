package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IF_CHAR;

/** LIS 4.13 section 7.10, cardholder debit/credit TCR0. */
public final class LisFinancialTcr0Packager extends LisFixedRecordPackager {
    public LisFinancialTcr0Packager() {
        super(
                new IFA_NUMERIC(2, "F001 TRANSACTION CODE"),
                new IFA_NUMERIC(6, "F002 FILE RECORD SEQUENCE"),
                new IFA_NUMERIC(1, "F003 TCR SEQUENCE"),
                new IFA_NUMERIC(1, "F004 ROUTE INDICATOR"),
                new IFA_NUMERIC(10, "F005 MERCHANT ESTABLISHMENT"),
                new IF_CHAR(25, "F006 MERCHANT NAME"),
                new IF_CHAR(13, "F007 MERCHANT CITY"),
                new IF_CHAR(3, "F008 MERCHANT COUNTRY"),
                new IFA_NUMERIC(4, "F009 MCC"),
                new IF_CHAR(1, "F010 MERCHANT TYPE"),
                new IF_CHAR(2, "F011 SPECIAL MERCHANT CONDITION"),
                new IF_CHAR(1, "F012 ELECTRONIC TERMINAL"),
                new IF_CHAR(8, "F013 TERMINAL ID"),
                new IFA_NUMERIC(1, "F014 USAGE CODE"),
                new IFA_NUMERIC(3, "F015 RECONCILIATION INDICATOR"),
                new IF_CHAR(50, "F016 MEMBER MESSAGE"),
                new IFA_NUMERIC(4, "F017 DISPUTE REASON"),
                new IFA_NUMERIC(6, "F018 CHARGEBACK REFERENCE"),
                new IF_CHAR(1, "F019 DOCUMENTATION INDICATOR"),
                new IFA_NUMERIC(1, "F020 PAYMENT PRODUCT"),
                new IF_CHAR(19, "F021 PAN"),
                new IFA_NUMERIC(4, "F022 EXPIRY DATE"),
                new IF_CHAR(1, "F023 CARDHOLDER AUTH METHOD"),
                new IF_CHAR(1, "F024 CAPTURE INDICATOR"),
                new IFA_NUMERIC(2, "F025 ATM ACCOUNT"),
                new IF_CHAR(5, "F026 TRANSACTION STATUS"),
                new IF_CHAR(81, "F027 FILLER")
        );
    }
}
