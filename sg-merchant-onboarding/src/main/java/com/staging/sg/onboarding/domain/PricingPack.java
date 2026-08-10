package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "onboarding_pricing_pack")
public class PricingPack {
    @Id @Column(name = "pack_code", length = 64) private String code;
    @Column(nullable = false, length = 160) private String label;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PricingPackStatus status;
    @Column(name = "created_by", nullable = false, length = 96, updatable = false) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected PricingPack() {}
    public static PricingPack draft(String code, String label, String actor) {
        PricingPack value = new PricingPack(); value.code = required(code, 64).toUpperCase();
        value.label = required(label, 160); value.createdBy = required(actor, 96);
        value.status = PricingPackStatus.DRAFT; value.createdAt = Instant.now(); value.updatedAt = value.createdAt;
        return value;
    }
    public void activate() { status = PricingPackStatus.ACTIVE; updatedAt = Instant.now(); }
    public void retire() { status = PricingPackStatus.RETIRED; updatedAt = Instant.now(); }
    private static String required(String value, int max) { if (value == null || value.isBlank()
            || value.trim().length() > max) throw new IllegalArgumentException("Invalid pricing value"); return value.trim(); }
    public String code() { return code; } public String label() { return label; }
    public PricingPackStatus status() { return status; } public long version() { return version; }
}
