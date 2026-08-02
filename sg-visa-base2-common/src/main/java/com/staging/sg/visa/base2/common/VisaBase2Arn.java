package com.staging.sg.visa.base2.common;

import java.time.LocalDate;

public final class VisaBase2Arn {
    private VisaBase2Arn() {}

    public static String generate(String acquiringIdentifier, LocalDate date, long locator) {
        if (acquiringIdentifier == null || !acquiringIdentifier.matches("\\d{6}"))
            throw new IllegalArgumentException("Official six-digit acquiring identifier is required");
        if (locator < 1 || locator > 99_999_999_999L) throw new IllegalArgumentException("Invalid ARN locator");
        String julian = Integer.toString(Math.floorMod(date.getYear(), 10))
                + "%03d".formatted(date.getDayOfYear());
        String body = "1" + acquiringIdentifier + julian + "%011d".formatted(locator);
        return body + checkDigit(body);
    }

    private static int checkDigit(String value) {
        int sum = 0; boolean doubleDigit = true;
        for (int i = value.length() - 1; i >= 0; i--) {
            int digit = value.charAt(i) - '0';
            if (doubleDigit) { digit *= 2; if (digit > 9) digit -= 9; }
            sum += digit; doubleDigit = !doubleDigit;
        }
        return (10 - sum % 10) % 10;
    }
}
