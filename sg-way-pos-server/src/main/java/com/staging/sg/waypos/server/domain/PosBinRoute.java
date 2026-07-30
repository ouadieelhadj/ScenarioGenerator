package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pos_bin_routes")
public class PosBinRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "bin_from", nullable = false, length = 11)
    private String binFrom;
    @Column(name = "bin_to", nullable = false, length = 11)
    private String binTo;
    @Column(name = "interface_code", nullable = false, length = 32)
    private String interfaceCode;
    @Column(name = "priority", nullable = false)
    private int priority;
    @Column(name = "active", nullable = false)
    private boolean active;

    protected PosBinRoute() {
    }

    public static PosBinRoute active(
            String binFrom, String binTo, String interfaceCode, int priority) {
        if (binFrom == null || binTo == null
                || !binFrom.matches("\\d{6,11}")
                || !binTo.matches("\\d{6,11}")
                || binFrom.length() != binTo.length()
                || binFrom.compareTo(binTo) > 0
                || interfaceCode == null
                || !interfaceCode.matches("[A-Z0-9_]{5,32}")) {
            throw new IllegalArgumentException("Invalid BIN route");
        }
        PosBinRoute value = new PosBinRoute();
        value.binFrom = binFrom;
        value.binTo = binTo;
        value.interfaceCode = interfaceCode;
        value.priority = priority;
        value.active = true;
        return value;
    }

    public Long getId() { return id; }
    public String getBinFrom() { return binFrom; }
    public String getBinTo() { return binTo; }
    public String getInterfaceCode() { return interfaceCode; }
    public int getPriority() { return priority; }
    public boolean isActive() { return active; }

    public void deactivate() {
        active = false;
    }
}
