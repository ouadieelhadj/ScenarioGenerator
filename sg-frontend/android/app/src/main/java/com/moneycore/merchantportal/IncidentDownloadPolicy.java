package com.moneycore.merchantportal;

/** Bounded file-copy envelope, independent of Android and of any server authorization. */
final class IncidentDownloadPolicy {
    static final int MAX_BYTES = 10_000_000;
    static final int MAX_BASE64 = 4 * ((MAX_BYTES + 2) / 3);
    private IncidentDownloadPolicy() {}

    static String extension(String mime) {
        if (mime == null) return null;
        return switch (mime) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> null;
        };
    }

    static boolean validName(String name, String mime) {
        String ext = extension(mime);
        return name != null && ext != null && name.matches("incident-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\" + ext);
    }

    static boolean validBase64(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_BASE64 || value.length() % 4 != 0) return false;
        int padding = value.endsWith("==") ? 2 : value.endsWith("=") ? 1 : 0;
        long size = (long) value.length() / 4 * 3 - padding;
        if (size < 1 || size > MAX_BYTES) return false;
        for (int i = 0; i < value.length() - padding; i++) {
            char c = value.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '/')) return false;
        }
        return true;
    }

    static boolean validContent(String mime, byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > MAX_BYTES) return false;
        return switch (mime == null ? "" : mime) {
            case "application/pdf" -> bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            case "image/jpeg" -> bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            case "image/png" -> bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            default -> false;
        };
    }
}
