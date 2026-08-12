package com.staging.sg.way4aura.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.way4aura.api.Way4DryRunRequest;
import com.staging.sg.way4aura.domain.*;
import com.staging.sg.way4aura.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class Way4DryRunService {
    private final boolean generationEnabled;
    private final Way4MappingService mapping;
    private final Way4XmlGenerator generator;
    private final Way4XsdValidator validator;
    private final Way4FileBatchRepository files;
    private final Way4ApplicationStateRepository applications;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Way4StagingStore stagingStore;
    public Way4DryRunService(@Value("${way4-aura.generation-enabled:false}") boolean generationEnabled,
            Way4MappingService mapping, Way4XmlGenerator generator, Way4XsdValidator validator,
            Way4FileBatchRepository files, Way4ApplicationStateRepository applications,
            EntityManager entityManager, ObjectMapper objectMapper, Way4StagingStore stagingStore) {
        this.generationEnabled = generationEnabled; this.mapping = mapping; this.generator = generator;
        this.validator = validator; this.files = files; this.applications = applications;
        this.entityManager = entityManager; this.objectMapper = objectMapper; this.stagingStore = stagingStore;
    }

    @Transactional
    public DryRunResult generateBatch(List<Way4DryRunRequest> requests, String idempotencyKey) {
        if (!generationEnabled) throw new AuraMappingBlockedException(
                "WAY4 generation is disabled until the 5A gate is formally lifted");
        if (requests == null || requests.isEmpty() || requests.size() > 500)
            throw new IllegalArgumentException("A WAY4 batch must contain between 1 and 500 merchants");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160)
            throw new IllegalArgumentException("Invalid WAY4 batch idempotency key");
        requests.forEach(this::requireRequest);
        if (requests.stream().map(Way4DryRunRequest::onboardingCaseId).distinct().count() != requests.size())
            throw new IllegalArgumentException("A merchant can occur only once in a WAY4 batch");
        String payloadHash = sha256(canonical(requests));
        Way4FileBatch existing = files.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null && !existing.payloadHash().equals(payloadHash))
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT: same key with a different payload");
        List<ResolvedWay4Application> resolved = requests.stream().map(mapping::resolve).toList();
        int mappingVersion = resolved.stream().mapToInt(ResolvedWay4Application::mappingVersion).max().orElseThrow();
        Way4FileBatch file = existing == null
                ? files.save(Way4FileBatch.draft(nextFileNumber(), idempotencyKey, payloadHash, mappingVersion))
                : existing;
        List<Way4ApplicationState> states = new ArrayList<>();
        for (Way4DryRunRequest request : requests)
            states.addAll(states(request, sha256(canonical(request)), existing != null));
        applications.flush();
        byte[] xml = generator.generate(resolved, file.fileNumber(), file.generatedAt());
        Way4XsdValidator.ValidationResult validation = validator.validate(xml);
        String xmlHash = sha256(xml);
        if (existing != null && existing.xmlSha256() != null && !existing.xmlSha256().equals(xmlHash))
            throw new IllegalStateException("REPLAY_DIVERGENCE: regenerated XML differs from the validated file");
        stagingStore.stage(file.extendedFileName(), xml, xmlHash);
        if (file.status() == Way4FileStatus.DRAFT) {
            file.validated(xmlHash, validation.xsdSha256()); file.staged(); files.save(file);
        }
        states.forEach(state -> { if (state.status() == Way4ApplicationStatus.PENDING) {
            state.generated(); applications.save(state); } });
        return result(file, payloadHash, xmlHash, validation.xsdSha256(), xml);
    }

    @Transactional
    public DryRunResult generate(Way4DryRunRequest request) {
        if (!generationEnabled) throw new AuraMappingBlockedException(
                "WAY4 generation is disabled until the 5A gate is formally lifted");
        requireRequest(request);
        String payloadHash = sha256(canonical(request));
        Way4FileBatch existing = files.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null && !existing.payloadHash().equals(payloadHash))
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT: same key with a different payload");
        ResolvedWay4Application resolved = mapping.resolve(request);
        Way4FileBatch file = existing == null
                ? files.save(Way4FileBatch.draft(nextFileNumber(), request.idempotencyKey(),
                        payloadHash, resolved.mappingVersion())) : existing;
        List<Way4ApplicationState> states = states(request, payloadHash, existing != null);
        applications.flush();
        byte[] xml = generator.generate(resolved, file.fileNumber(), file.generatedAt());
        Way4XsdValidator.ValidationResult validation = validator.validate(xml);
        String xmlHash = sha256(xml);
        if (existing != null && existing.xmlSha256() != null && !existing.xmlSha256().equals(xmlHash))
            throw new IllegalStateException("REPLAY_DIVERGENCE: regenerated XML differs from the validated file");
        if (file.status() == Way4FileStatus.DRAFT) {
            file.validated(xmlHash, validation.xsdSha256()); files.save(file);
        }
        states.forEach(state -> { if (state.status() == Way4ApplicationStatus.PENDING) {
            state.generated(); applications.save(state); } });
        return new DryRunResult(file.id(), file.fileNumber(), file.extendedFileName(),
                file.status().name(), file.payloadHash(), xmlHash, validation.xsdSha256(),
                file.mappingVersion(), new String(xml, StandardCharsets.UTF_8));
    }

    private List<Way4ApplicationState> states(Way4DryRunRequest request, String hash,
            boolean idempotentReplay) {
        List<Source> sources = new ArrayList<>();
        sources.add(new Source("CLIENT",request.onboardingCaseId(),Way4RegNumbers.client(request.applicationRegNumber())));
        sources.add(new Source("ACCOUNT",stable(request.onboardingCaseId()+":ACCOUNT"),Way4RegNumbers.account(request.applicationRegNumber())));
        sources.add(new Source("ADDRESS",stable(request.onboardingCaseId()+":ADDRESS"),Way4RegNumbers.address(request.applicationRegNumber())));
        if(request.outlets()!=null)for(var outlet:request.outlets())if(outlet.terminalRequests()!=null)for(var terminal:outlet.terminalRequests())
            for(int ordinal=1;ordinal<=terminal.quantity();ordinal++)sources.add(new Source("DEVICE",stable(terminal.sourceRequestId()+":"+ordinal),
                    Way4RegNumbers.device(request.applicationRegNumber(),terminal.sourceRequestId(),ordinal)));
        return sources.stream().map(source -> applications.findBySourceTypeAndSourceId(
                source.type(), source.id()).map(current -> {
                    if (!current.payloadHash().equals(hash)) throw new IllegalStateException(
                            "APPLICATION_PAYLOAD_CONFLICT: source object changed after generation");
                    if (!idempotentReplay && current.status() != Way4ApplicationStatus.PENDING)
                        throw new IllegalStateException(
                                "APPLICATION_ALREADY_GENERATED: use the original batch idempotency key");
                    return current;
                }).orElseGet(() -> applications.save(Way4ApplicationState.pending(
                        source.type(), source.id(), source.regNumber(), hash)))).toList();
    }
    private long nextFileNumber() {
        return ((Number) entityManager.createNativeQuery("select nextval('way4_file_number_seq')")
                .getSingleResult()).longValue();
    }
    private void requireRequest(Way4DryRunRequest request) {
        if (request == null || request.onboardingCaseId() == null || request.applicationRegNumber() == null
                || !request.applicationRegNumber().matches("[A-Za-z0-9._-]{1,64}") || request.idempotencyKey() == null
                || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 160)
            throw new IllegalArgumentException("Incomplete WAY4 dry-run request");
    }
    private String canonical(Way4DryRunRequest request) {
        try { return objectMapper.writeValueAsString(request); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot fingerprint request", exception); }
    }
    private String canonical(Object request) {
        try { return objectMapper.writeValueAsString(request); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot fingerprint request", exception); }
    }
    private DryRunResult result(Way4FileBatch file, String payloadHash, String xmlHash,
            String xsdHash, byte[] xml) {
        return new DryRunResult(file.id(), file.fileNumber(), file.extendedFileName(),
                file.status().name(), payloadHash, xmlHash, xsdHash, file.mappingVersion(),
                new String(xml, StandardCharsets.UTF_8));
    }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private static UUID stable(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private record Source(String type, UUID id, String regNumber) {}
    public record DryRunResult(UUID fileId, long fileNumber, String fileName, String status,
            String payloadSha256, String xmlSha256, String xsdSha256, int mappingVersion, String xml) {}
}
