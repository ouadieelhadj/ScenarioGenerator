package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_pricing_pack_version", uniqueConstraints =
        @UniqueConstraint(name = "uk_pricing_pack_version", columnNames = {"pack_code","version_number"}))
public class PricingPackVersion {
    @Id private UUID id;
    @Column(name = "pack_code", nullable = false, length = 64, updatable = false) private String packCode;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @Column(name = "terms_json", nullable = false, columnDefinition = "TEXT", updatable = false) private String termsJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PricingPackStatus status;
    @Column(name = "valid_from") private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "created_by", nullable = false, length = 96, updatable = false) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "activated_by", length = 96) private String activatedBy;
    @Version private long version;
    protected PricingPackVersion() {}
    public static PricingPackVersion draft(String code, int number, String json, String actor) {
        if (number < 1 || json == null || json.isBlank()) throw new IllegalArgumentException("TAR-001: invalid pricing version");
        PricingPackVersion value = new PricingPackVersion(); value.id = UUID.randomUUID();
        value.packCode = code; value.versionNumber = number; value.termsJson = json;
        value.status = PricingPackStatus.DRAFT; value.createdBy = actor; value.createdAt = Instant.now(); return value;
    }
    public void activate(String actor) { if (status != PricingPackStatus.DRAFT) throw new IllegalStateException("Only draft pricing can be activated");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor is required");
        status = PricingPackStatus.ACTIVE; validFrom = Instant.now(); activatedBy = actor.trim(); }
    public void retire() { if (status == PricingPackStatus.ACTIVE) { status = PricingPackStatus.RETIRED; validTo = Instant.now(); } }
    public UUID id() { return id; } public String packCode() { return packCode; }
    public int versionNumber() { return versionNumber; } public String termsJson() { return termsJson; }
    public PricingPackStatus status() { return status; }
}
