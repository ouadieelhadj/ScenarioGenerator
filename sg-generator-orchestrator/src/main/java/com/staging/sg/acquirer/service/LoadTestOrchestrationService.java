package com.staging.sg.acquirer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquirer.orchestration.DmasClient;
import com.staging.sg.acquirer.report.ExcelReportGenerator;
import com.staging.sg.acquirer.report.PdfReportGenerator;
import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestration d'un test de CHARGE : lit le Test (parametrage + 1er tps_step),
 * cree une Execution, delegue la charge a sg-dmas-acquirer (POST /loadtest sur la
 * connexion permanente jPOS), poll le resultat, persiste Execution + Result,
 * et genere les rapports Excel/PDF.
 */
@Service
public class LoadTestOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestOrchestrationService.class);

    private final ExecutionRepository executionRepository;
    private final ResultRepository    resultRepository;
    private final TestRepository      testRepository;
    private final UserRepository      userRepository;
    private final TpsStepRepository   tpsStepRepository;
    private final DmasClient          dmasClient;
    private final ObjectMapper        objectMapper = new ObjectMapper();

    @Value("${dmas.acquirer.base-url:http://localhost:8084}") private String acquirerUrl;
    @Value("${dmas.login:admin}")        private String dmasLogin;
    @Value("${dmas.password:Admin123!}") private String dmasPassword;
    @Value("${dmas.loadtest.max-concurrency:200}") private int maxConcurrency;
    @Value("${dmas.loadtest.report-dir:reports}")  private String reportBaseDir;
    @Value("${dmas.loadtest.poll-interval-ms:1000}") private long pollIntervalMs;

    public LoadTestOrchestrationService(ExecutionRepository executionRepository,
                                        ResultRepository resultRepository,
                                        TestRepository testRepository,
                                        UserRepository userRepository,
                                        TpsStepRepository tpsStepRepository,
                                        DmasClient dmasClient) {
        this.executionRepository = executionRepository;
        this.resultRepository    = resultRepository;
        this.testRepository      = testRepository;
        this.userRepository      = userRepository;
        this.tpsStepRepository   = tpsStepRepository;
        this.dmasClient          = dmasClient;
    }

    /** Lance le load test (async). Retourne l'info de demarrage (executionId). */
    public Map<String,Object> start(Long testId, String userLogin) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found : " + testId));
        List<TpsStep> steps = tpsStepRepository.findByTestIdOrderByStepOrderAsc(testId);
        if (steps.isEmpty())
            throw new RuntimeException("Le test " + testId + " n'a pas de tps_step (charge non definie)");
        User user = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new RuntimeException("User not found : " + userLogin));

        TpsStep step = steps.get(0);  // premier step
        int tpsValue    = step.getTpsValue() != null ? step.getTpsValue() : 1;
        int durationSec = (step.getEndSeconds() != null ? step.getEndSeconds() : 1)
                        - (step.getStartSeconds() != null ? step.getStartSeconds() : 0);
        if (durationSec <= 0) durationSec = 1;
        int concurrency = Math.min(tpsValue, maxConcurrency);

        // Parametrage message depuis le config JSON du test
        String pan = "0000000000000000", amount = "000000000000", entryMode = "CARD_PRESENT";
        try {
            if (test.getConfig() != null && !test.getConfig().isBlank()) {
                Map<?,?> cfg = objectMapper.readValue(test.getConfig(), Map.class);
                if (cfg.get("DE002_PAN") != null) pan = String.valueOf(cfg.get("DE002_PAN"));
                if (cfg.get("DE004_AMOUNT") != null) {
                    long amt = ((Number) cfg.get("DE004_AMOUNT")).longValue();
                    amount = String.format("%012d", amt);
                }
                if (cfg.get("ENTRY_MODE") != null) entryMode = String.valueOf(cfg.get("ENTRY_MODE"));
            }
        } catch (Exception e) {
            log.warn("[LOADTEST-ORCH] parse config : {}", e.getMessage());
        }

        Execution exec = new Execution();
        exec.setTest(test);
        exec.setUser(user);
        exec.setMode(ExecutionMode.CHARGE);
        exec.setStatus(ExecutionStatus.RUNNING);
        exec.setTpsTarget(tpsValue);
        exec.setDurationSeconds(durationSec);
        exec.setStartedAt(LocalDateTime.now());
        exec = executionRepository.save(exec);

        final Long execId = exec.getId();
        final String testName = test.getName();
        final String fPan = pan, fAmount = amount, fEntry = entryMode;
        final int fDur = durationSec, fTps = tpsValue, fConc = concurrency;

        Thread worker = new Thread(() ->
                runAndPersist(execId, testName, fPan, fAmount, fEntry, fDur, fTps, fConc),
                "loadtest-orch-" + execId);
        worker.setDaemon(true);
        worker.start();

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("executionId", execId);
        r.put("testId", testId);
        r.put("testName", testName);
        r.put("mode", "CHARGE");
        r.put("status", "RUNNING");
        r.put("tpsTarget", tpsValue);
        r.put("durationSeconds", durationSec);
        r.put("concurrency", concurrency);
        return r;
    }

    private void runAndPersist(Long execId, String testName, String pan, String amount,
                               String entryMode, int durationSec, int tpsValue, int concurrency) {
        try {
            String token = dmasClient.ensureToken(acquirerUrl, dmasLogin, dmasPassword);

            // 1. Lancer le load test cote acquereur
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("pan", pan);
            body.put("amount", amount);
            body.put("entryMode", entryMode);
            body.put("durationSeconds", durationSec);
            body.put("targetTps", tpsValue);
            body.put("concurrency", concurrency);
            String resp = dmasClient.postJson(acquirerUrl, "/api/admin/dmas/loadtest", token, body);
            Map<?,?> startJson = dmasClient.parse(resp);
            String loadTestId = String.valueOf(startJson.get("loadTestId"));
            if (loadTestId == null || "null".equals(loadTestId)) {
                throw new RuntimeException("loadtest refuse : " + resp);
            }
            log.info("[LOADTEST-ORCH] exec={} loadTestId={} lance", execId, loadTestId);

            // 2. Poll jusqu'a COMPLETED (timeout global = duree + marge)
            long deadline = System.currentTimeMillis() + (durationSec + 60L) * 1000L;
            Map<?,?> status = null;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(pollIntervalMs);
                String s = dmasClient.getJson(acquirerUrl,
                        "/api/admin/dmas/loadtest/" + loadTestId + "/status?details=true", token);
                status = dmasClient.parse(s);
                if ("COMPLETED".equals(String.valueOf(status.get("status")))
                        || "ERROR".equals(String.valueOf(status.get("status")))) break;
            }

            // 3. Mapper les details -> Result + agreger -> Execution
            List<Result> results = new ArrayList<>();
            int approved = 0, declined = 0, errors = 0;
            long sumMs = 0; long minMs = Long.MAX_VALUE; long maxMs = 0;
            List<Long> latencies = new ArrayList<>();
            Execution exec = executionRepository.findById(execId).orElseThrow();

            Object detailsObj = status != null ? status.get("details") : null;
            if (detailsObj instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?,?> d)) continue;
                    Result r = new Result();
                    r.setExecution(exec);
                    r.setPanMasked(maskPan(pan));
                    String de39 = String.valueOf(d.get("de39"));
                    r.setDe039(de39 != null && de39.length() > 2 ? de39.substring(0,2) : de39);
                    boolean ok = Boolean.TRUE.equals(d.get("approved"));
                    r.setApproved(ok);
                    long dur = d.get("durationMs") != null ? ((Number) d.get("durationMs")).longValue() : 0;
                    r.setDurationMs((int) dur);
                    if (d.get("requestHex") != null)  r.setRequestHex(String.valueOf(d.get("requestHex")));
                    if (d.get("responseHex") != null) r.setResponseHex(String.valueOf(d.get("responseHex")));
                    r.setExecutedAt(LocalDateTime.now());
                    results.add(r);

                    if (Boolean.TRUE.equals(d.get("error"))) errors++;
                    else if (ok) approved++; else declined++;
                    sumMs += dur; minMs = Math.min(minMs, dur); maxMs = Math.max(maxMs, dur);
                    latencies.add(dur);
                }
            }
            int total = results.size();

            // 4. Agregats -> Execution
            exec.setTxTotal(total);
            exec.setTxSent(total);
            exec.setTxApproved(approved);
            exec.setTxDeclined(declined);
            if (total > 0) {
                exec.setResponseTimeAvg(BigDecimal.valueOf(sumMs / (double) total).setScale(2, RoundingMode.HALF_UP));
                exec.setResponseTimeMin(BigDecimal.valueOf(minMs == Long.MAX_VALUE ? 0 : minMs));
                exec.setResponseTimeMax(BigDecimal.valueOf(maxMs));
                Collections.sort(latencies);
                exec.setResponseTimeP95(BigDecimal.valueOf(percentile(latencies, 95)));
                exec.setResponseTimeP99(BigDecimal.valueOf(percentile(latencies, 99)));
            }
            long durMs = status != null && status.get("durationMs") != null
                    ? ((Number) status.get("durationMs")).longValue() : 1;
            exec.setTpsActualAvg(BigDecimal.valueOf(durMs > 0 ? total * 1000.0 / durMs : 0).setScale(2, RoundingMode.HALF_UP));
            exec.setEndedAt(LocalDateTime.now());
            exec.setStatus(ExecutionStatus.COMPLETED);

            // 5. Persister
            executionRepository.save(exec);
            if (!results.isEmpty()) resultRepository.saveAll(results);

            // 6. Rapports Excel + PDF
            try {
                Path dir = Paths.get(reportBaseDir);
                Files.createDirectories(dir);
                Path xlsx = dir.resolve("loadtest-" + execId + ".xlsx");
                Path pdf  = dir.resolve("loadtest-" + execId + ".pdf");
                ExcelReportGenerator.generate(xlsx, exec, testName, results);
                PdfReportGenerator.generate(pdf, exec, testName, results);
                exec.setReportDir(dir.toAbsolutePath().toString());
                exec.setReportExcel(xlsx.toAbsolutePath().toString());
                exec.setReportPdf(pdf.toAbsolutePath().toString());
                executionRepository.save(exec);
                log.info("[LOADTEST-ORCH] exec={} rapports generes : {} / {}", execId, xlsx, pdf);
            } catch (Exception re) {
                log.error("[LOADTEST-ORCH] exec={} erreur rapports : {}", execId, re.getMessage());
            }

            log.info("[LOADTEST-ORCH] exec={} COMPLETED total={} approved={} declined={} errors={}",
                    execId, total, approved, declined, errors);
        } catch (Exception e) {
            log.error("[LOADTEST-ORCH] exec={} erreur : {}", execId, e.getMessage(), e);
            executionRepository.findById(execId).ifPresent(ex -> {
                ex.setStatus(ExecutionStatus.ERROR);
                ex.setEndedAt(LocalDateTime.now());
                executionRepository.save(ex);
            });
        }
    }

    private static double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }
}
