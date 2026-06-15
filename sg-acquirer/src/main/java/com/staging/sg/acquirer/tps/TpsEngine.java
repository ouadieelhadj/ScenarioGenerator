package com.staging.sg.acquirer.tps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquirer.acquirer.McAcquirer;
import com.staging.sg.acquirer.acquirer.McAuthRequest;
import com.staging.sg.acquirer.acquirer.McAuthResult;
import com.staging.sg.acquirer.report.ReportService;
import com.staging.sg.common.dto.TpsStepDto;
import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.ExecutionRepository;
import com.staging.sg.common.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class TpsEngine {

    private static final Logger log = LoggerFactory.getLogger(TpsEngine.class);

    private final McAcquirer          acquirer;
    private final ExecutionRepository executionRepository;
    private final ResultRepository    resultRepository;
    private final ReportService       reportService;
    private final ObjectMapper        objectMapper;

    @Value("${tps.max:100}")
    private int maxTps;

    @Value("${tps.thread-pool-size:20}")
    private int threadPoolSize;

    private final Map<Long, TpsExecution> activeExecutions = new ConcurrentHashMap<>();

    public TpsEngine(McAcquirer acquirer,
                     ExecutionRepository executionRepository,
                     ResultRepository resultRepository,
                     ReportService reportService,
                     ObjectMapper objectMapper) {
        this.acquirer            = acquirer;
        this.executionRepository = executionRepository;
        this.resultRepository    = resultRepository;
        this.reportService       = reportService;
        this.objectMapper        = objectMapper;
    }

    // ── Start ────────────────────────────────────────────────

    public TpsExecution start(Execution execution, Test test, boolean persist) {
        Long executionId = execution.getId();
        TpsMetrics metrics = new TpsMetrics();

        // Extract steps + config before thread
        List<TpsStepDto> steps = extractSteps(test);
        String config   = test.getConfig();
        String testName = test.getName();

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        Future<?> future = executor.submit(() ->
            runExecution(executionId, testName, config, steps, metrics, executor, persist));

        TpsExecution tpsExecution = new TpsExecution(executionId, test.getId(), metrics, future);
        activeExecutions.put(executionId, tpsExecution);

        log.info("[TPS] Execution {} started — test={} steps={} persist={}",
                executionId, testName, steps.size(), persist);
        return tpsExecution;
    }

    // ── Stop ─────────────────────────────────────────────────

    public void stop(Long executionId) {
        TpsExecution exec = activeExecutions.get(executionId);
        if (exec != null) {
            exec.stop();
            activeExecutions.remove(executionId);
            saveFinal(executionId, ExecutionStatus.STOPPED, exec.getMetrics(), false);
            log.info("[TPS] Execution {} stopped", executionId);
        }
    }

    // ── Getters ──────────────────────────────────────────────

    public TpsMetrics getMetrics(Long executionId) {
        TpsExecution exec = activeExecutions.get(executionId);
        return exec != null ? exec.getMetrics() : null;
    }

    public boolean isRunning(Long executionId) {
        TpsExecution exec = activeExecutions.get(executionId);
        return exec != null && exec.isRunning();
    }

    // ── Run execution ────────────────────────────────────────

    private void runExecution(Long executionId,
                               String testName,
                               String config,
                               List<TpsStepDto> steps,
                               TpsMetrics metrics,
                               ExecutorService executor,
                               boolean persist) {
        try {
            McAuthRequest baseRequest = parseConfig(config);

            if (steps.isEmpty()) {
                // Mode SIMPLE
                log.info("[TPS] Mode SIMPLE");
                long start = System.currentTimeMillis();
                McAuthResult result = acquirer.authorize(baseRequest);
                long duration = System.currentTimeMillis() - start;
                metrics.record(
                    result.getDE002_PAN(), result.getDE039_RESPONSE_CODE(),
                    result.getDE038_AUTH_CODE(), result.isApproved(),
                    duration, result.getRequestHex(), result.getResponseHex());
                metrics.setStatus("COMPLETED");
                finalize(executionId, testName, metrics, persist);
                return;
            }

            // Mode CHARGE
            log.info("[TPS] Mode CHARGE — {} steps", steps.size());

            for (int s = 0; s < steps.size(); s++) {
                TpsStepDto step = steps.get(s);
                metrics.setCurrentStep(s + 1);

                int tpsValue    = Math.min(step.getTpsValue(), maxTps);
                int durationSec = step.getEndSeconds() - step.getStartSeconds();
                metrics.setCurrentTps(tpsValue);

                TpsMetrics.StepRecord stepRecord = new TpsMetrics.StepRecord(
                        s + 1, tpsValue, step.getStartSeconds(), step.getEndSeconds());

                log.info("[TPS] Step {}/{} — {} TPS for {}s",
                        s + 1, steps.size(), tpsValue, durationSec);

                long intervalMs   = tpsValue > 0 ? 1000L / tpsValue : 1000L;
                long stepEnd      = System.currentTimeMillis() + (durationSec * 1000L);
                long secondStart  = System.currentTimeMillis();
                int  txThisSecond = 0;
                int  stepTxBefore = metrics.getTxTotal();

                while (System.currentTimeMillis() < stepEnd) {
                    if (Thread.currentThread().isInterrupted()) {
                        metrics.setStatus("STOPPED");
                        return;
                    }

                    final McAuthRequest req = cloneRequest(baseRequest);
                    executor.submit(() -> {
                        long start = System.currentTimeMillis();
                        try {
                            McAuthResult result = acquirer.authorize(req);
                            long duration = System.currentTimeMillis() - start;
                            metrics.record(
                                result.getDE002_PAN(),
                                result.getDE039_RESPONSE_CODE(),
                                result.getDE038_AUTH_CODE(),
                                result.isApproved(), duration,
                                result.getRequestHex(),
                                result.getResponseHex());
                        } catch (Exception e) {
                            long duration = System.currentTimeMillis() - start;
                            metrics.record(false, duration);
                            log.warn("[TPS] TX error : {}", e.getMessage());
                        }
                    });
                    txThisSecond++;

                    long now = System.currentTimeMillis();
                    if (now - secondStart >= 1000) {
                        double actualTps = txThisSecond * 1000.0 / (now - secondStart);
                        metrics.recordTps(actualTps);
                        secondStart   = now;
                        txThisSecond  = 0;
                    }

                    Thread.sleep(intervalMs);
                }

                // Save step record
                stepRecord.txSent     = metrics.getTxTotal() - stepTxBefore;
                stepRecord.txApproved = metrics.getTxApproved();
                metrics.addStepRecord(stepRecord);
            }

            // Wait for remaining transactions
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            metrics.setStatus("COMPLETED");

            log.info("[TPS] Execution {} COMPLETED — TX={} approved={} declined={} avgTPS={} avgMs={}",
                    executionId, metrics.getTxTotal(), metrics.getTxApproved(),
                    metrics.getTxDeclined(),
                    String.format("%.1f", metrics.getAvgTps()),
                    String.format("%.0f", metrics.getAvgResponseMs()));

            finalize(executionId, testName, metrics, persist);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.setStatus("STOPPED");
            saveFinal(executionId, ExecutionStatus.STOPPED, metrics, false);
        } catch (Exception e) {
            log.error("[TPS] Execution {} error : {}", executionId, e.getMessage(), e);
            metrics.setStatus("ERROR");
            saveFinal(executionId, ExecutionStatus.ERROR, metrics, false);
        } finally {
            activeExecutions.remove(executionId);
        }
    }

    // ── Finalize — TXT + persist + PDF + Excel ────────────────

    private void finalize(Long executionId, String testName,
                           TpsMetrics metrics, boolean persist) {
        // 1. Generate TXT from memory
        reportService.generateTxtReport(executionId, testName, metrics);

        // 2. Save to database
        saveFinal(executionId, ExecutionStatus.COMPLETED, metrics, persist);

        // 3. Generate PDF + Excel from database
        if (persist) {
            reportService.generatePdfReport(executionId);
            reportService.generateExcelReport(executionId);
        }
    }

    // ── Save final ───────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFinal(Long executionId, ExecutionStatus status,
                           TpsMetrics metrics, boolean persist) {
        try {
            executionRepository.findById(executionId).ifPresent(exec -> {
                exec.setStatus(status);
                exec.setEndedAt(LocalDateTime.now());
                exec.setTxTotal(metrics.getTxTotal());
                exec.setTxApproved(metrics.getTxApproved());
                exec.setTxDeclined(metrics.getTxDeclined());
                exec.setTpsActualAvg(BigDecimal.valueOf(metrics.getAvgTps()));
                exec.setResponseTimeAvg(BigDecimal.valueOf(metrics.getAvgResponseMs()));
                exec.setResponseTimeMin(BigDecimal.valueOf(metrics.getMinResponseMs()));
                exec.setResponseTimeMax(BigDecimal.valueOf(metrics.getMaxResponseMs()));
                exec.setResponseTimeP95(BigDecimal.valueOf(metrics.getP95ResponseMs()));
                exec.setResponseTimeP99(BigDecimal.valueOf(metrics.getP99ResponseMs()));
                executionRepository.save(exec);
                log.info("[TPS] Execution {} saved — status={} TX={} avgTPS={} avgMs={}",
                        executionId, status, metrics.getTxTotal(),
                        String.format("%.1f", metrics.getAvgTps()),
                        String.format("%.0f", metrics.getAvgResponseMs()));
            });

            // Batch insert results
            if (persist && !metrics.getTxRecords().isEmpty()) {
                Execution execution = executionRepository.findById(executionId).orElse(null);
                if (execution != null) {
                    List<Result> results = new ArrayList<>();
                    for (TpsMetrics.TxRecord rec : metrics.getTxRecords()) {
                        Result r = new Result();
                        r.setExecution(execution);
                        r.setPanMasked(rec.panMasked);
                        r.setDe039(rec.de039);
                        r.setDe038AuthCode(rec.de038AuthCode);
                        r.setApproved(rec.approved);
                        r.setDurationMs((int) rec.durationMs);
                        r.setRequestHex(rec.requestHex);
                        r.setResponseHex(rec.responseHex);
                        r.setExecutedAt(LocalDateTime.now());
                        results.add(r);
                    }
                    resultRepository.saveAll(results);
                    log.info("[TPS] {} results saved to database", results.size());
                }
            }
        } catch (Exception e) {
            log.error("[TPS] Error saving final : {}", e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private List<TpsStepDto> extractSteps(Test test) {
        List<TpsStepDto> steps = new ArrayList<>();
        if (test.getTpsSteps() != null) {
            test.getTpsSteps().forEach(s -> {
                TpsStepDto dto = new TpsStepDto();
                dto.setStepOrder(s.getStepOrder());
                dto.setStartSeconds(s.getStartSeconds());
                dto.setEndSeconds(s.getEndSeconds());
                dto.setTpsValue(s.getTpsValue());
                steps.add(dto);
            });
        }
        return steps;
    }

    @SuppressWarnings("unchecked")
    private McAuthRequest parseConfig(String config) {
        McAuthRequest request = new McAuthRequest();
        try {
            if (config != null && !config.isBlank()) {
                Map<String, Object> map = objectMapper.readValue(config, Map.class);
                if (map.containsKey("DE002_PAN"))
                    request.setDE002_PAN((String) map.get("DE002_PAN"));
                if (map.containsKey("DE004_AMOUNT"))
                    request.setDE004_AMOUNT(((Number) map.get("DE004_AMOUNT")).longValue());
                if (map.containsKey("DE003_PROCESSING_CODE"))
                    request.setDE003_PROCESSING_CODE((String) map.get("DE003_PROCESSING_CODE"));
                if (map.containsKey("DE018_MCC"))
                    request.setDE018_MCC((String) map.get("DE018_MCC"));
                if (map.containsKey("DE022_POS_ENTRY_MODE"))
                    request.setDE022_POS_ENTRY_MODE((String) map.get("DE022_POS_ENTRY_MODE"));
                if (map.containsKey("DE049_CURRENCY_CODE"))
                    request.setDE049_CURRENCY_CODE((String) map.get("DE049_CURRENCY_CODE"));
                if (map.containsKey("DE052_PIN"))
                    request.setDE052_PIN((String) map.get("DE052_PIN"));
            }
        } catch (Exception e) {
            log.warn("[TPS] Error parsing config : {}", e.getMessage());
        }
        return request;
    }

    private McAuthRequest cloneRequest(McAuthRequest o) {
        McAuthRequest c = new McAuthRequest();
        c.setDE002_PAN(o.getDE002_PAN());
        c.setDE004_AMOUNT(o.getDE004_AMOUNT());
        c.setDE003_PROCESSING_CODE(o.getDE003_PROCESSING_CODE());
        c.setDE018_MCC(o.getDE018_MCC());
        c.setDE022_POS_ENTRY_MODE(o.getDE022_POS_ENTRY_MODE());
        c.setDE049_CURRENCY_CODE(o.getDE049_CURRENCY_CODE());
        c.setDE052_PIN(o.getDE052_PIN());
        return c;
    }
}
