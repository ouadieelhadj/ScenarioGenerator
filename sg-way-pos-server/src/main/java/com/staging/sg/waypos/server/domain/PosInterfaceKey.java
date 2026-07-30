package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pos_interface_keys")
public class PosInterfaceKey {
    @Id
    @Column(name = "interface_code", length = 32)
    private String interfaceCode;
    @Column(name = "pek_under_lmk", nullable = false)
    private String pekUnderLmk;
    @Column(name = "pek_kcv", nullable = false, length = 6)
    private String pekKcv;
    @Column(name = "pek_length", nullable = false)
    private int pekLength;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Version
    private long version;

    protected PosInterfaceKey() {}

    public static PosInterfaceKey active(
            String interfaceCode, String pekUnderLmk, String pekKcv, int pekLength) {
        PosInterfaceKey key = new PosInterfaceKey();
        key.interfaceCode = interfaceCode;
        key.pekUnderLmk = pekUnderLmk;
        key.pekKcv = pekKcv;
        key.pekLength = pekLength;
        key.active = true;
        return key;
    }

    public String getPekUnderLmk() { return pekUnderLmk; }
    public String getPekKcv() { return pekKcv; }
    public int getPekLength() { return pekLength; }
    public boolean isActive() { return active; }

    public void replace(String underLmk, String kcv, int length) {
        pekUnderLmk = underLmk;
        pekKcv = kcv;
        pekLength = length;
        active = true;
    }
}
