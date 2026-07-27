package com.staging.sg.swam.lis.common.service;

import com.staging.sg.swam.lis.common.model.LisFinancialData;
import com.staging.sg.swam.lis.common.model.LisFinancialRecord;
import org.jpos.iso.ISOMsg;

import java.time.format.DateTimeFormatter;

/** Maps normalized clearing data to LIS 4.13 financial TCR0/TCR1 records. */
public final class LisFinancialRecordFactory {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("ddMMyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    public LisFinancialRecord create(LisFinancialData data) {
        String tc = switch (safe(data.transactionType())) {
            case "CASH_WITHDRAWAL" -> "07";
            case "CASH_ADVANCE" -> "06";
            default -> "05";
        };
        ISOMsg tcr0 = new ISOMsg();
        String[] zero = {
                tc, "", "", "0", numeric(data.merchantId(), 10), alpha(data.merchantName(), 25),
                alpha(data.merchantCity(), 13), alpha(defaulted(data.merchantCountry(), "MAR"), 3),
                numeric(defaulted(data.mcc(), "0000"), 4), " ", "  ", "2",
                alpha(data.terminalId(), 8), data.clearingCycle() > 1 ? "2" : "1",
                "000", " ".repeat(50), "0000", "000000", " ", "1",
                alpha(data.pan(), 19), numeric(defaulted(data.expiryDate(), "0000"), 4),
                "2", "I", "00", "00000", ""
        };
        set(tcr0, zero);

        String date = data.transactionAt() == null ? "000000" : DATE.format(data.transactionAt());
        String time = data.transactionAt() == null ? "000000" : TIME.format(data.transactionAt());
        long billing = data.billingAmount() == null ? data.settlementAmount() : data.billingAmount();
        ISOMsg tcr1 = new ISOMsg();
        String[] one = {
                tc, "", "", date, alpha(data.authorizationCode(), 6), "2", "0",
                alpha(data.rrn(), 23), numeric(data.acquirerInstitutionId(), 8), " ", "2",
                amount(data.transactionAmount()), numeric(data.issuerInstitutionId(), 8), " ", "2",
                amount(data.settlementAmount()), amount(data.settlementAmount()), amount(0),
                date, date, "   ", " ", "00", "0000",
                numeric(data.cardSequenceNumber(), 3), date, alpha(data.rrn(), 12), time,
                numeric(defaulted(data.transactionCurrency(), "504"), 3),
                numeric(defaulted(data.settlementCurrency(), "504"), 3),
                amount(0), amount(0), alpha(data.ecommerceIndicator(), 1), amount(billing),
                "000000000", "000000000", "0000000", ""
        };
        set(tcr1, one);
        return new LisFinancialRecord(tcr0, tcr1);
    }

    private static void set(ISOMsg message, String[] values) {
        for (int field = 0; field < values.length; field++) message.set(field, values[field]);
    }

    private static String amount(long value) {
        if (value < 0 || value > 999_999_999_999L) throw new IllegalArgumentException("LIS amount exceeds n12");
        return "%012d".formatted(value);
    }

    private static String numeric(String value, int length) {
        String digits = safe(value).replaceAll("\\D", "");
        if (digits.length() > length) digits = digits.substring(digits.length() - length);
        return "0".repeat(length - digits.length()) + digits;
    }

    private static String alpha(String value, int length) {
        String ascii = safe(value).replaceAll("[^\\x20-\\x7E]", " ");
        return (ascii.length() > length ? ascii.substring(0, length) : ascii)
                + " ".repeat(Math.max(0, length - ascii.length()));
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
