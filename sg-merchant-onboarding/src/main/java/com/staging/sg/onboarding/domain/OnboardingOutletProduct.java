package com.staging.sg.onboarding.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_outlet_product", uniqueConstraints =
        @UniqueConstraint(name = "uk_onboarding_outlet_product",
                columnNames = {"outlet_id", "product_id"}))
public class OnboardingOutletProduct {
    @Id private UUID id;
    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "outlet_id", nullable = false, updatable = false) private UUID outletId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(name = "pricing_pack_code", length = 64) private String pricingPackCode;
    @Column(name = "pricing_pack_version") private Integer pricingPackVersion;
    @Column(name = "pricing_snapshot_json", columnDefinition = "TEXT") private String pricingSnapshotJson;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected OnboardingOutletProduct() {}

    public static OnboardingOutletProduct create(UUID caseId, UUID outletId, UUID productId,
            String pricingPackCode, Integer pricingPackVersion, String pricingSnapshotJson) {
        if (caseId == null || outletId == null || productId == null)
            throw new IllegalArgumentException("PDV-005: case, outlet and product are required");
        if ((pricingPackCode == null) != (pricingPackVersion == null))
            throw new IllegalArgumentException("TAR-001: pricing pack code and version are inseparable");
        if (pricingPackVersion != null && pricingPackVersion < 1)
            throw new IllegalArgumentException("TAR-001: pricing pack version is invalid");
        OnboardingOutletProduct value = new OnboardingOutletProduct();
        value.id = UUID.randomUUID();
        value.caseId = caseId;
        value.outletId = outletId;
        value.productId = productId;
        value.pricingPackCode = optional(pricingPackCode, 64);
        value.pricingPackVersion = pricingPackVersion;
        value.pricingSnapshotJson = optional(pricingSnapshotJson, 16000);
        value.active = true;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void deactivate() { active = false; updatedAt = Instant.now(); }
    public void applyPricingSnapshot(String packCode, int packVersion, String snapshotJson) {
        if (!active || packCode == null || packCode.isBlank() || packVersion < 1
                || snapshotJson == null || snapshotJson.isBlank())
            throw new IllegalArgumentException("TAR-002: invalid pricing snapshot");
        this.pricingPackCode = packCode;
        this.pricingPackVersion = packVersion;
        this.pricingSnapshotJson = snapshotJson;
        this.updatedAt = Instant.now();
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException("Value is too long");
        return value.trim();
    }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public UUID outletId() { return outletId; }
    public UUID productId() { return productId; }
    public String pricingPackCode() { return pricingPackCode; }
    public Integer pricingPackVersion() { return pricingPackVersion; }
    public String pricingSnapshotJson() { return pricingSnapshotJson; }
    public boolean active() { return active; }
}
