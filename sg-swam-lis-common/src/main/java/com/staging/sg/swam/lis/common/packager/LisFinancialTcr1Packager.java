package com.staging.sg.swam.lis.common.packager;

import org.jpos.iso.IFA_NUMERIC;
import org.jpos.iso.IF_CHAR;

/** LIS 4.13 section 7.11, cardholder debit/credit TCR1. */
public final class LisFinancialTcr1Packager extends LisFixedRecordPackager {
    public LisFinancialTcr1Packager() {
        super(
                new IFA_NUMERIC(2, "F001 TRANSACTION CODE"),
                new IFA_NUMERIC(6, "F002 FILE RECORD SEQUENCE"),
                new IFA_NUMERIC(1, "F003 TCR SEQUENCE"),
                new IFA_NUMERIC(6, "F004 TRANSACTION DATE"),
                new IF_CHAR(6, "F005 AUTHORISATION CODE"),
                new IF_CHAR(1, "F006 AUTHORISATION SOURCE"),
                new IF_CHAR(1, "F007 TRANSACTION TYPE"),
                new IF_CHAR(23, "F008 ACQUIRER REFERENCE"),
                new IFA_NUMERIC(8, "F009 FORWARDING INSTITUTION"),
                new IF_CHAR(1, "F010 VOID INDICATOR"),
                new IFA_NUMERIC(1, "F011 SOURCE EXPONENT"),
                new IFA_NUMERIC(12, "F012 SOURCE AMOUNT"),
                new IFA_NUMERIC(8, "F013 RECEIVING INSTITUTION"),
                new IF_CHAR(1, "F014 SPECIAL CHARGEBACK"),
                new IFA_NUMERIC(1, "F015 DESTINATION EXPONENT"),
                new IFA_NUMERIC(12, "F016 DESTINATION AMOUNT"),
                new IFA_NUMERIC(12, "F017 LOCAL CURRENCY AMOUNT"),
                new IFA_NUMERIC(12, "F018 ISSUER REIMBURSEMENT FEE"),
                new IFA_NUMERIC(6, "F019 VALUE DATE"),
                new IFA_NUMERIC(6, "F020 PROCESSING DATE"),
                new IF_CHAR(3, "F021 STATE PROVINCE"),
                new IF_CHAR(1, "F022 FILLER"),
                new IFA_NUMERIC(2, "F023 DEPOSITING BANK"),
                new IFA_NUMERIC(4, "F024 DEPOSITING BRANCH"),
                new IFA_NUMERIC(3, "F025 CARD SEQUENCE"),
                new IFA_NUMERIC(6, "F026 RECONCILIATION DATE"),
                new IF_CHAR(12, "F027 RETRIEVAL REFERENCE"),
                new IFA_NUMERIC(6, "F028 TRANSACTION TIME"),
                new IFA_NUMERIC(3, "F029 SOURCE CURRENCY"),
                new IFA_NUMERIC(3, "F030 DESTINATION CURRENCY"),
                new IFA_NUMERIC(12, "F031 MERCHANT SERVICE CHARGE"),
                new IFA_NUMERIC(12, "F032 ACQUIRER MSC REVENUE"),
                new IF_CHAR(1, "F033 ECOMMERCE INDICATOR"),
                new IFA_NUMERIC(12, "F034 CARDHOLDER BILLING AMOUNT"),
                new IFA_NUMERIC(9, "F035 SOURCE TO MAD RATE"),
                new IFA_NUMERIC(9, "F036 MAD TO DESTINATION RATE"),
                new IFA_NUMERIC(7, "F037 CLEARING CONVERSION ID"),
                new IF_CHAR(25, "F038 FILLER")
        );
    }
}
