package com.staging.sg.swam.lis.common.service;

import com.staging.sg.swam.lis.common.model.LisFinancialRecord;
import org.jpos.iso.ISOMsg;

/** Derives a chargeback or second-presentation record from its original presentation. */
public final class LisDisputeRecordFactory {
    public LisFinancialRecord create(LisFinancialRecord original, String transactionCode,
            int cycle, String reasonCode, String reference, long amount, String message) {
        if (!java.util.Set.of("15","16","17","05","06","07").contains(transactionCode))
            throw new IllegalArgumentException("Invalid LIS dispute transaction code");
        if (cycle < 1 || cycle > 2) throw new IllegalArgumentException("Invalid dispute cycle");
        ISOMsg zero = copy(original.tcr0());
        ISOMsg one = copy(original.tcr1());
        zero.set(0, transactionCode); one.set(0, transactionCode);
        zero.set(13, Integer.toString(cycle));
        zero.set(15, fit(message, 50));
        zero.set(16, digits(reasonCode, 4));
        zero.set(17, digits(reference, 6));
        String encodedAmount = "%012d".formatted(amount);
        one.set(11, encodedAmount);
        one.set(15, encodedAmount);
        one.set(16, encodedAmount);
        return new LisFinancialRecord(zero, one);
    }

    private static ISOMsg copy(ISOMsg source) {
        ISOMsg target = new ISOMsg();
        for (int field = 0; field <= 64; field++) {
            if (source.hasField(field)) target.set(field, source.getString(field));
        }
        return target;
    }
    private static String fit(String value, int length) {
        String safe = value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", " ");
        safe = safe.length() > length ? safe.substring(0, length) : safe;
        return safe + " ".repeat(length-safe.length());
    }
    private static String digits(String value, int length) {
        String safe = value == null ? "" : value.replaceAll("\\D", "");
        if (safe.length() > length) safe = safe.substring(safe.length()-length);
        return "0".repeat(length-safe.length())+safe;
    }
}
