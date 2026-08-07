package com.staging.sg.switchlab.bff.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.switchlab.contracts.*;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SwitchLabTestCenterService {
    private static final List<String> MODULES = List.of(
            "sg-way-pos-simulator", "sg-mc-dmas-mastercard", "sg-mc-sms-issuer", "sg-dmcs-issuer",
            "sg-swam-issuer", "sg-swam-lis-switch", "sg-visa-visanet-simulator",
            "sg-visa-base2-network-simulator", "sg-merchant-site-simulator",
            "sg-visa-mastercard-gateway-simulator", "sg-3ds-network-simulator");
    private static final Set<String> EVIDENCE_TYPES = Set.of("JUNIT", "PLAYWRIGHT", "CERTIFICATION");
    private static final Set<String> SENSITIVE_MANIFEST_KEYS = Set.of(
            "pan", "pin", "key", "secret", "track2", "password", "cryptogram", "cvv", "cvc");

    private final SwitchLabOverviewService overview;
    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final Map<String, SwitchLabCampaign> campaigns = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<SwitchLabCampaignReport> reports = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<SwitchLabEvidence> evidence = new ConcurrentLinkedDeque<>();

    public SwitchLabTestCenterService(SwitchLabOverviewService overview, ObjectMapper objectMapper,
                                      @Value("${switchlab.test-center.store-path:runtime/switchlab/test-center.json}") String storePath) {
        this.overview = overview;
        this.objectMapper = objectMapper;
        this.storePath = Path.of(storePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void load() {
        if (!Files.isRegularFile(storePath)) return;
        try {
            StoreSnapshot snapshot = objectMapper.readValue(storePath.toFile(), StoreSnapshot.class);
            if (snapshot.campaigns() != null) snapshot.campaigns().forEach(item -> campaigns.put(item.id(), item));
            if (snapshot.reports() != null) snapshot.reports().forEach(reports::addLast);
            if (snapshot.evidence() != null) snapshot.evidence().forEach(evidence::addLast);
        } catch (IOException invalidStore) {
            throw new IllegalStateException("Cannot load SwitchLab Test Center store: " + storePath, invalidStore);
        }
    }

    public List<SwitchLabTestCatalogItem> catalog() {
        List<SwitchLabTestCatalogItem> result = new ArrayList<>();
        for (String module : MODULES) {
            result.add(new SwitchLabTestCatalogItem("HEALTH." + module.toUpperCase(),
                    "Availability — " + module, module, network(module), "HEALTH",
                    "AUTOMATED", true, List.of()));
        }
        result.add(new SwitchLabTestCatalogItem("POS.MCD01.TEST.01.SCENARIO.01",
                "Mastercard MCD01 contactless sentinel", "sg-way-pos-simulator", "MASTERCARD",
                "CERTIFICATION", "INTERACTIVE", false,
                List.of("secret://certification/mcd01/pan", "secret://certification/mcd01/pin")));
        return List.copyOf(result);
    }

    public List<SwitchLabProfileCapability> profiles() {
        return List.of(
                new SwitchLabProfileCapability("FUNCTIONAL", "Functional", true, null),
                profile("LOAD", "Load"), profile("STRESS", "Stress"),
                profile("ENDURANCE", "Endurance"), profile("SPIKE", "Spike"));
    }

    public List<SwitchLabCampaign> campaigns() {
        return campaigns.values().stream().sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
    }

    public SwitchLabCampaign create(SwitchLabCampaignRequest request) {
        if (request.name() == null || request.name().isBlank()) throw badRequest("Campaign name is required");
        if (request.testCodes() == null || request.testCodes().isEmpty()) throw badRequest("At least one test is required");
        Set<String> known = catalog().stream().map(SwitchLabTestCatalogItem::code).collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(request.testCodes())) throw badRequest("Campaign contains an unknown test");
        String profile = request.profile() == null ? "FUNCTIONAL" : request.profile().toUpperCase();
        if (profiles().stream().noneMatch(item -> item.code().equals(profile))) throw badRequest("Unknown execution profile");
        Map<String, String> refs = request.dataReferences() == null ? Map.of() : request.dataReferences();
        refs.forEach((key, value) -> { if (!isReference(value)) throw badRequest("Data values must be references, never clear values: " + key); });
        String id = UUID.randomUUID().toString();
        SwitchLabCampaign campaign = new SwitchLabCampaign(id, request.name().trim(), request.description(),
                List.copyOf(request.testCodes()), profile,
                boundedPercent(request.minimumAvailabilityPercent()), Math.max(1, request.maximumResponseTimeMs()),
                bounded(request.durationSeconds(), 1, 30, 5), bounded(request.targetTps(), 1, 50, 1),
                bounded(request.concurrency(), 1, 16, 1),
                Map.copyOf(refs), "DRAFT", Instant.now());
        campaigns.put(id, campaign);
        persist();
        return campaign;
    }

    public SwitchLabCampaignReport run(String campaignId, String environmentId, String correlationId) {
        SwitchLabCampaign campaign = campaign(campaignId);
        SwitchLabProfileCapability profile = profiles().stream().filter(item -> item.code().equals(campaign.profile()))
                .findFirst().orElseThrow();
        if (!profile.supported()) throw new ResponseStatusException(HttpStatus.CONFLICT, profile.reason());
        Map<String, SwitchLabTestCatalogItem> items = new LinkedHashMap<>();
        catalog().forEach(item -> items.put(item.code(), item));
        if (campaign.testCodes().stream().map(items::get).anyMatch(item -> item == null || !item.executable())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign contains an interactive or unavailable test");
        }
        if (overview.environments().stream().noneMatch(item -> item.id().equals(environmentId)))
            throw badRequest("Unknown SwitchLab environment");
        Instant started = Instant.now();
        List<Sample> samples = executeProfile(campaign, items);
        List<SwitchLabCampaignTestResult> results = aggregate(campaign, items, samples);
        int successes = samples.stream().mapToInt(sample -> sample.up() ? 1 : 0).sum();
        int errors = samples.size() - successes;
        double availability = samples.isEmpty() ? 0 : successes * 100.0 / samples.size();
        double errorRate = samples.isEmpty() ? 100 : errors * 100.0 / samples.size();
        long p95 = percentile(samples.stream().map(Sample::elapsedMillis).sorted().toList(), 0.95);
        Instant completed = Instant.now();
        long elapsed = completed.toEpochMilli() - started.toEpochMilli();
        String verdict = availability >= campaign.minimumAvailabilityPercent()
                && p95 <= campaign.maximumResponseTimeMs() ? "PASSED" : "FAILED";
        SwitchLabCampaignReport report = new SwitchLabCampaignReport(UUID.randomUUID().toString(), campaign.id(),
                environmentId, "COMPLETED", verdict, availability, campaign.minimumAvailabilityPercent(), elapsed,
                samples.size(), errorRate, p95,
                correlationId, started, completed, List.copyOf(results));
        reports.addFirst(report);
        while (reports.size() > 200) reports.pollLast();
        persist();
        return report;
    }

    public List<SwitchLabCampaignReport> reports() { return reports.stream().limit(100).toList(); }
    public SwitchLabCampaignReport report(String executionId) {
        return reports.stream().filter(item -> item.executionId().equals(executionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    public SwitchLabEvidence importEvidence(SwitchLabEvidenceRequest request, String correlationId) {
        String type = request.sourceType() == null ? "" : request.sourceType().toUpperCase();
        if (!EVIDENCE_TYPES.contains(type)) throw badRequest("Unsupported evidence type");
        if (request.sourceReference() == null || !isReference(request.sourceReference()))
            throw badRequest("Evidence must use a controlled reference");
        if (request.total() < 0 || request.passed() < 0 || request.failed() < 0
                || request.passed() + request.failed() > request.total()) throw badRequest("Invalid evidence counters");
        SwitchLabEvidence item = new SwitchLabEvidence(UUID.randomUUID().toString(), type, request.name(),
                request.sourceReference(), request.total(), request.passed(), request.failed(),
                request.failed() == 0 ? "PASSED" : "FAILED", correlationId, Instant.now());
        evidence.addFirst(item);
        while (evidence.size() > 200) evidence.pollLast();
        persist();
        return item;
    }

    public List<SwitchLabEvidence> evidence() { return evidence.stream().limit(100).toList(); }

    public SwitchLabEvidence analyzeCertificationManifest(Map<String, Object> manifest, String correlationId) {
        if (manifest == null || manifest.isEmpty()) throw badRequest("Certification manifest is required");
        rejectSensitiveManifestFields(manifest);
        String name = requiredManifestText(manifest, "name");
        requiredManifestText(manifest, "network");
        requiredManifestText(manifest, "schemaVersion");
        String sourceReference = requiredManifestText(manifest, "sourceReference");
        if (!isReference(sourceReference)) throw badRequest("Manifest must use a controlled source reference");
        Object cases = manifest.containsKey("tests") ? manifest.get("tests") : manifest.get("steps");
        if (!(cases instanceof List<?> items) || items.isEmpty())
            throw badRequest("Manifest must contain a non-empty tests or steps list");
        SwitchLabEvidence item = new SwitchLabEvidence(UUID.randomUUID().toString(), "CERTIFICATION", name,
                sourceReference, items.size(), 0, 0, "PENDING_REVIEW", correlationId, Instant.now());
        evidence.addFirst(item);
        while (evidence.size() > 200) evidence.pollLast();
        persist();
        return item;
    }
    private List<Sample> executeProfile(SwitchLabCampaign campaign, Map<String, SwitchLabTestCatalogItem> items) {
        List<Sample> samples = new ArrayList<>();
        int duration = "FUNCTIONAL".equals(campaign.profile()) ? 1 : campaign.durationSeconds();
        int cursor = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(campaign.concurrency())) {
            for (int second = 0; second < duration && samples.size() < 2_000; second++) {
                long tick = System.nanoTime();
                int rate = rate(campaign, second, duration);
                List<Future<Sample>> futures = new ArrayList<>();
                for (int i = 0; i < rate && samples.size() + futures.size() < 2_000; i++) {
                    String testCode = campaign.testCodes().get(cursor++ % campaign.testCodes().size());
                    SwitchLabTestCatalogItem test = items.get(testCode);
                    futures.add(executor.submit(() -> probe(test)));
                }
                for (Future<Sample> future : futures) {
                    try { samples.add(future.get()); }
                    catch (Exception failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Probe execution failed"); }
                }
                if (!"FUNCTIONAL".equals(campaign.profile()) && second + 1 < duration) {
                    long remaining = 1_000 - (System.nanoTime() - tick) / 1_000_000;
                    if (remaining > 0) Thread.sleep(remaining);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Campaign interrupted");
        }
        return samples;
    }

    private Sample probe(SwitchLabTestCatalogItem test) {
        long started = System.nanoTime();
        boolean up;
        try { up = "UP".equals(overview.probeComponent(test.moduleCode()).status()); }
        catch (RuntimeException unavailable) { up = false; }
        return new Sample(test.code(), up, (System.nanoTime() - started) / 1_000_000);
    }

    private List<SwitchLabCampaignTestResult> aggregate(SwitchLabCampaign campaign,
                                                        Map<String, SwitchLabTestCatalogItem> items,
                                                        List<Sample> samples) {
        List<SwitchLabCampaignTestResult> results = new ArrayList<>();
        for (String code : campaign.testCodes()) {
            List<Sample> selected = samples.stream().filter(sample -> sample.testCode().equals(code)).toList();
            int successes = selected.stream().mapToInt(sample -> sample.up() ? 1 : 0).sum();
            int errors = selected.size() - successes;
            double availability = selected.isEmpty() ? 0 : successes * 100.0 / selected.size();
            long p95 = percentile(selected.stream().map(Sample::elapsedMillis).sorted().toList(), 0.95);
            String verdict = availability >= campaign.minimumAvailabilityPercent()
                    && p95 <= campaign.maximumResponseTimeMs() ? "PASSED" : "FAILED";
            results.add(new SwitchLabCampaignTestResult(code, items.get(code).moduleCode(),
                    "availability >= " + campaign.minimumAvailabilityPercent() + "% and p95 <= "
                            + campaign.maximumResponseTimeMs() + "ms",
                    String.format(java.util.Locale.ROOT, "availability=%.2f%%, p95=%dms", availability, p95),
                    verdict, selected.stream().mapToLong(Sample::elapsedMillis).sum(), selected.size(),
                    successes, errors, p95));
        }
        return results;
    }

    private int rate(SwitchLabCampaign campaign, int second, int duration) {
        if ("FUNCTIONAL".equals(campaign.profile())) return campaign.testCodes().size();
        if ("STRESS".equals(campaign.profile()))
            return Math.max(1, (int) Math.ceil(campaign.targetTps() * (second + 1.0) / duration));
        if ("SPIKE".equals(campaign.profile()))
            return second >= duration / 3 && second < Math.max(1, duration * 2 / 3) ? campaign.targetTps() : 1;
        return campaign.targetTps();
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(values.size() * percentile) - 1);
        return values.get(Math.min(index, values.size() - 1));
    }

    private int bounded(int value, int min, int max, int fallback) {
        int selected = value <= 0 ? fallback : value;
        return Math.max(min, Math.min(max, selected));
    }
    private void rejectSensitiveManifestFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                if (SENSITIVE_MANIFEST_KEYS.stream().anyMatch(key::contains))
                    throw badRequest("Sensitive field is forbidden in certification manifest: " + entry.getKey());
                rejectSensitiveManifestFields(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            list.forEach(this::rejectSensitiveManifestFields);
        }
    }
    private String requiredManifestText(Map<String, Object> manifest, String key) {
        Object value = manifest.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw badRequest("Manifest field is required: " + key);
        return text.trim();
    }
    private synchronized void persist() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),
                    new StoreSnapshot(campaigns(), reports.stream().toList(), evidence.stream().toList()));
            try {
                Files.move(temporary, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, storePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException persistenceFailure) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot persist SwitchLab Test Center state", persistenceFailure);
        }
    }
    private SwitchLabCampaign campaign(String id) { SwitchLabCampaign value = campaigns.get(id); if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"); return value; }
    private SwitchLabProfileCapability profile(String code, String label) { return new SwitchLabProfileCapability(code, label, true, "Bounded simulator health-probe profile; no member engine"); }
    private boolean isReference(String value) { return value != null && (value.startsWith("secret://") || value.startsWith("vault://") || value.startsWith("env://") || value.startsWith("artifact://")); }
    private double boundedPercent(double value) { return Math.max(0, Math.min(100, value)); }
    private String network(String module) { if (module.contains("visa")) return "VISA"; if (module.contains("mc-") || module.contains("dmcs")) return "MASTERCARD"; if (module.contains("swam")) return "SWAM"; if (module.contains("3ds") || module.contains("merchant") || module.contains("gateway")) return "ECOMMERCE"; return "POS"; }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private record Sample(String testCode, boolean up, long elapsedMillis) { }
    private record StoreSnapshot(List<SwitchLabCampaign> campaigns,
                                 List<SwitchLabCampaignReport> reports,
                                 List<SwitchLabEvidence> evidence) { }
}
