package com.staging.sg.common.iso;

import java.util.ArrayList;
import java.util.List;

/** LTV codec used by OpenWay POS character private fields such as DE63. */
public final class WayPosPrivateData {
    public record Item(String tableId, String value) {}

    private WayPosPrivateData() {}

    public static List<Item> decode(String data) {
        if (data == null || data.isEmpty()) return List.of();
        List<Item> items = new ArrayList<>();
        int offset = 0;
        while (offset < data.length()) {
            if (offset + 3 > data.length()
                    || !data.substring(offset, offset + 3).matches("\\d{3}")) {
                throw new IllegalArgumentException("Invalid private-data length");
            }
            int length = Integer.parseInt(data.substring(offset, offset + 3));
            offset += 3;
            if (length < 2 || offset + length > data.length()) {
                throw new IllegalArgumentException("Truncated private-data item");
            }
            String table = data.substring(offset, offset + 2);
            String value = data.substring(offset + 2, offset + length);
            items.add(new Item(table, value));
            offset += length;
        }
        return List.copyOf(items);
    }

    public static String encode(List<Item> items) {
        StringBuilder result = new StringBuilder();
        for (Item item : items) {
            if (item.tableId() == null || item.tableId().length() != 2
                    || item.value() == null) {
                throw new IllegalArgumentException("Invalid private-data item");
            }
            int length = 2 + item.value().length();
            if (length > 999) throw new IllegalArgumentException("Private-data item too large");
            result.append("%03d".formatted(length))
                    .append(item.tableId()).append(item.value());
        }
        return result.toString();
    }
}
