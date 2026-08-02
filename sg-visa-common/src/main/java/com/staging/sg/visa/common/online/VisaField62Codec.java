package com.staging.sg.visa.common.online;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sandbox representation of the F62 subfields used by the first E2E. */
public final class VisaField62Codec {
    private VisaField62Codec() {}

    public static String encode(String aci, String transactionId, String validationCode) {
        return item("01", aci) + item("02", transactionId) + item("03", validationCode);
    }

    public static VisaOnlineReferences decode(String value) {
        Map<String, String> fields = new LinkedHashMap<>();
        int offset = 0;
        while (value != null && offset < value.length()) {
            if (offset + 5 > value.length()) throw new IllegalArgumentException("Malformed Visa F62");
            String id = value.substring(offset, offset + 2);
            int length;
            try { length = Integer.parseInt(value.substring(offset + 2, offset + 5)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Malformed Visa F62 length", e); }
            offset += 5;
            if (offset + length > value.length()) throw new IllegalArgumentException("Truncated Visa F62");
            fields.put(id, value.substring(offset, offset + length));
            offset += length;
        }
        return new VisaOnlineReferences(fields.get("01"), fields.get("02"), fields.get("03"),
                "SIMULATED_NETWORK");
    }

    private static String item(String id, String value) {
        if (value == null || value.isBlank() || value.length() > 999) {
            throw new IllegalArgumentException("Missing or oversized Visa F62 subfield " + id);
        }
        return id + "%03d".formatted(value.length()) + value;
    }
}
