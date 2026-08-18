package com.staging.sg.fraud.service;

import java.util.Locale;

public final class FraudSector {
    public static final String MONETIQUE = "MONETIQUE";
    public static final String MOBILE_BANKING = "MOBILE_BANKING";
    private FraudSector() {}

    public static String normalize(String sector, String channel) {
        String value = sector == null || sector.isBlank() ? channel : sector;
        value = value == null ? "" : value.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "MONETIQUE", "PAYMENTS", "CARD", "3DS", "ATM", "POS", "ECOMMERCE", "ISO8583" -> MONETIQUE;
            case "MOBILE_BANKING", "MOBILE_PAYMENT", "TRANSFER", "INTERNET_BANKING", "WALLET" -> MOBILE_BANKING;
            default -> throw new IllegalArgumentException("Unsupported fraud sector");
        };
    }
}
