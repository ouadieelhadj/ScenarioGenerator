package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pos_security_keys")
public class PosSecurityKey {
    @Id
    @Column(name = "key_code", length = 32)
    private String keyCode;
    @Column(name = "key_type", nullable = false, length = 8)
    private String keyType;
    @Column(name = "key_under_lmk", nullable = false)
    private String keyUnderLmk;
    @Column(name = "kcv", nullable = false, length = 6)
    private String kcv;
    @Column(name = "key_length", nullable = false)
    private int keyLength;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Version
    private long version;

    protected PosSecurityKey() {}

    public static PosSecurityKey active(
            String code, String type, String underLmk, String kcv, int length) {
        PosSecurityKey key = new PosSecurityKey();
        key.keyCode = code;
        key.keyType = type;
        key.keyUnderLmk = underLmk;
        key.kcv = kcv;
        key.keyLength = length;
        key.active = true;
        return key;
    }

    public String getKeyUnderLmk() { return keyUnderLmk; }
    public String getKcv() { return kcv; }
    public int getKeyLength() { return keyLength; }
    public boolean isActive() { return active; }

    public void replace(String type, String underLmk, String newKcv, int length) {
        keyType = type;
        keyUnderLmk = underLmk;
        kcv = newKcv;
        keyLength = length;
        active = true;
    }
}
