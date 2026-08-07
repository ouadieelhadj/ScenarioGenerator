package com.staging.sg.acquiring.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AcquiringIdentifierAllocator {
    private final JdbcTemplate jdbc;

    public AcquiringIdentifierAllocator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String nextMid() { return next("acquiring_mid_sequence", 15); }
    public String nextTid() { return next("acquiring_tid_sequence", 8); }

    private String next(String sequence, int width) {
        Long value = jdbc.queryForObject("SELECT nextval('" + sequence + "')", Long.class);
        if (value == null) throw new IllegalStateException("Identifier sequence returned no value");
        String formatted = String.format("%0" + width + "d", value);
        if (formatted.length() != width) throw new IllegalStateException("Identifier sequence exhausted: " + sequence);
        return formatted;
    }
}
