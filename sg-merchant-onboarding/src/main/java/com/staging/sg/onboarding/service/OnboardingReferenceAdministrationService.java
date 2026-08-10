package com.staging.sg.onboarding.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.onboarding.domain.*;
import com.staging.sg.onboarding.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingReferenceAdministrationService {
    private final OnboardingReferenceValueRepository references;
    private final OnboardingReferenceAuditRepository audits;
    private final ObjectMapper objectMapper;

    public OnboardingReferenceAdministrationService(OnboardingReferenceValueRepository references,
            OnboardingReferenceAuditRepository audits, ObjectMapper objectMapper) {
        this.references = references; this.audits = audits; this.objectMapper = objectMapper;
    }

    @Transactional
    public OnboardingReferenceValue create(String category, String code, String label,
            String attributesJson, String actor) {
        OnboardingReferenceValueId id = new OnboardingReferenceValueId(category.trim().toUpperCase(),
                code.trim().toUpperCase());
        if (references.existsById(id)) throw new IllegalStateException("Reference value already exists");
        OnboardingReferenceValue value = references.save(OnboardingReferenceValue.active(
                id.category(), id.code(), label, normalizedJson(attributesJson), actor));
        audit(value, "CREATE", null, snapshot(value), actor);
        return value;
    }

    @Transactional
    public OnboardingReferenceValue update(String category, String code, String label,
            String attributesJson, long expectedVersion, String actor) {
        OnboardingReferenceValue value = get(category, code);
        requireVersion(value, expectedVersion);
        String before = snapshot(value);
        value.update(label, normalizedJson(attributesJson), actor);
        references.save(value); audit(value, "UPDATE", before, snapshot(value), actor);
        return value;
    }

    @Transactional
    public OnboardingReferenceValue setActive(String category, String code, boolean active,
            long expectedVersion, String actor) {
        OnboardingReferenceValue value = get(category, code);
        requireVersion(value, expectedVersion);
        String before = snapshot(value);
        if (active) value.activate(actor); else value.deactivate(actor);
        references.save(value); audit(value, active ? "ACTIVATE" : "DEACTIVATE",
                before, snapshot(value), actor);
        return value;
    }

    private OnboardingReferenceValue get(String category, String code) {
        return references.findById(new OnboardingReferenceValueId(
                category.toUpperCase(), code.toUpperCase())).orElseThrow(() ->
                new IllegalArgumentException("Reference value not found"));
    }
    private static void requireVersion(OnboardingReferenceValue value, long expected) {
        if (value.version() != expected) throw new IllegalStateException("CONCURRENCY: reference version is stale");
    }
    private String normalizedJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.writeValueAsString(objectMapper.readTree(json)); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("attributesJson is invalid JSON"); }
    }
    private String snapshot(OnboardingReferenceValue value) {
        try { return objectMapper.writeValueAsString(new Snapshot(value.category(), value.code(),
                value.label(), value.active(), value.attributesJson(), value.validFrom(), value.validTo(), value.version())); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot audit reference", exception); }
    }
    private void audit(OnboardingReferenceValue value, String action, String before,
            String after, String actor) {
        audits.save(OnboardingReferenceAudit.create(value.category(), value.code(), action,
                before, after, actor));
    }
    private record Snapshot(String category, String code, String label, boolean active,
            String attributesJson, java.time.Instant validFrom, java.time.Instant validTo, long version) {}
}
