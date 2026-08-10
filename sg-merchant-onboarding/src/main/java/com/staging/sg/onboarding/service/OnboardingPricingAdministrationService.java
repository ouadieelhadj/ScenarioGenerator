package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OnboardingPricingAdministrationService {
    private final PricingPackRepository packs;
    private final PricingPackVersionRepository versions;
    private final TariffDeviationRepository deviations;
    private final OnboardingOutletProductRepository outletProducts;
    private final ObjectMapper objectMapper;
    public OnboardingPricingAdministrationService(PricingPackRepository packs,
            PricingPackVersionRepository versions, TariffDeviationRepository deviations,
            OnboardingOutletProductRepository outletProducts, ObjectMapper objectMapper) {
        this.packs = packs; this.versions = versions; this.deviations = deviations;
        this.outletProducts = outletProducts; this.objectMapper = objectMapper;
    }

    @Transactional
    public PricingPack createPack(String code, String label, String actor) {
        String normalized = code.trim().toUpperCase();
        if (packs.existsById(normalized)) throw new IllegalStateException("Pricing pack already exists");
        return packs.save(PricingPack.draft(normalized, label, actor));
    }
    @Transactional
    public PricingPackVersion createVersion(String code, int version, String termsJson, String actor) {
        String normalized = code.trim().toUpperCase();
        if (!packs.existsById(normalized)) throw new IllegalArgumentException("Pricing pack not found");
        if (versions.existsByPackCodeAndVersionNumber(normalized, version))
            throw new IllegalStateException("Pricing pack version already exists");
        return versions.save(PricingPackVersion.draft(normalized, version, jsonObject(termsJson), actor));
    }
    @Transactional
    public PricingPackVersion activate(String code, int version, String actor) {
        String normalized = code.toUpperCase();
        PricingPackVersion target = versions.findByPackCodeAndVersionNumber(normalized, version)
                .orElseThrow(() -> new IllegalArgumentException("Pricing pack version not found"));
        versions.findByPackCodeAndStatus(normalized, PricingPackStatus.ACTIVE).forEach(value -> {
            value.retire(); versions.save(value);
        });
        target.activate(actor); versions.save(target);
        PricingPack pack = packs.findById(normalized).orElseThrow(); pack.activate(); packs.save(pack);
        return target;
    }
    @Transactional
    public TariffDeviation requestDeviation(UUID outletProductId, String packCode,
            int packVersion, String afterJson, String reason, String actor) {
        OnboardingOutletProduct product = outletProducts.findById(outletProductId)
                .orElseThrow(() -> new IllegalArgumentException("Outlet product not found"));
        PricingPackVersion pack = activeVersion(packCode, packVersion);
        String before = product.pricingSnapshotJson() == null ? pack.termsJson() : product.pricingSnapshotJson();
        return deviations.save(TariffDeviation.request(outletProductId, pack.packCode(),
                pack.versionNumber(), before, jsonObject(afterJson), reason, actor));
    }
    @Transactional
    public TariffDeviation approveDeviation(UUID id, long expectedVersion, String actor) {
        TariffDeviation deviation = deviation(id, expectedVersion); deviation.approve(actor);
        OnboardingOutletProduct product = outletProducts.findById(deviation.outletProductId()).orElseThrow();
        if (product.pricingSnapshotJson() != null && !product.pricingSnapshotJson().equals(deviation.beforeJson()))
            throw new IllegalStateException("CONCURRENCY: pricing snapshot changed after deviation request");
        product.applyPricingSnapshot(deviation.packCode(), deviation.packVersion(), deviation.afterJson());
        outletProducts.save(product); return deviations.save(deviation);
    }
    @Transactional
    public TariffDeviation rejectDeviation(UUID id, long expectedVersion, String reason, String actor) {
        TariffDeviation deviation = deviation(id, expectedVersion); deviation.reject(actor, reason);
        return deviations.save(deviation);
    }
    private PricingPackVersion activeVersion(String code, int version) {
        PricingPackVersion value = versions.findByPackCodeAndVersionNumber(code.toUpperCase(), version)
                .orElseThrow(() -> new IllegalArgumentException("Pricing pack version not found"));
        if (value.status() != PricingPackStatus.ACTIVE) throw new IllegalStateException("Pricing pack version is not active");
        return value;
    }
    private TariffDeviation deviation(UUID id, long expected) {
        TariffDeviation value = deviations.findById(id).orElseThrow(() -> new IllegalArgumentException("Deviation not found"));
        if (value.version() != expected) throw new IllegalStateException("CONCURRENCY: deviation version is stale"); return value;
    }
    private String jsonObject(String value) {
        try { var node = objectMapper.readTree(value); if (node == null || !node.isObject())
            throw new IllegalArgumentException("Pricing terms must be a JSON object"); return objectMapper.writeValueAsString(node); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Pricing terms are invalid JSON"); }
    }
}
