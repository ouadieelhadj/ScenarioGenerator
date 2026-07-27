package com.staging.sg.swam.lis.common.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class SidClearingRules {
    private static final DateTimeFormatter SID_DATE_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private SidClearingRules() {
    }

    public static String transactionType(String processingCode, String mcc) {
        if ("6011".equals(mcc)) {
            return "CASH_WITHDRAWAL";
        }
        if (processingCode != null && processingCode.startsWith("01")) {
            return "CASH_ADVANCE";
        }
        return "PURCHASE";
    }

    public static LocalDateTime transactionAt(String localTransactionDt, LocalDateTime fallback) {
        if (localTransactionDt == null || localTransactionDt.length() != 12) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(localTransactionDt, SID_DATE_TIME);
        } catch (DateTimeParseException exception) {
            return fallback;
        }
    }

    public static String merchantName(String merchantNameLocation) {
        return slice(merchantNameLocation, 0, 25);
    }

    public static String merchantCity(String merchantNameLocation) {
        return slice(merchantNameLocation, 25, 38);
    }

    private static String slice(String value, int start, int end) {
        if (value == null || value.length() <= start) {
            return null;
        }
        return value.substring(start, Math.min(end, value.length())).trim();
    }
}
