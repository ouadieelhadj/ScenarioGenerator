package com.staging.sg.visa.common.online;

public final class VisaDataMasking {
    private VisaDataMasking() {}

    public static String pan(String pan) {
        if (pan == null || pan.length() < 10) return "REDACTED";
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }
}
