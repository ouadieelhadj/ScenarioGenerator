package com.staging.sg.acquirer.tps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquirer.acquirer.McAcquirer;
import com.staging.sg.acquirer.acquirer.McAuthRequest;
import com.staging.sg.acquirer.acquirer.McAuthResult;
import com.staging.sg.common.dto.TpsStepDto;
import com.staging.sg.common.entity.Execution;
import com.staging.sg.common.entity.ExecutionStatus;
import com.staging.sg.common.entity.Result;
import com.staging.sg.common.entity.Test;
import com.staging.sg.common.repository.ExecutionRepository;
import com.staging.sg.common.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * TPS Engine — sends transactions at configured rate.
 *
 * Flow :
 *   1. Read TPS steps from test config
 *   2. For each step : send X TPS during Y seconds
 *   3. Record metrics
 *   4. Save results to database
 *   5. Update execution status
 */
@Service
public class TpsEngine {

    private static final Logger log = LoggerFactory.getLogger(TpsEngine.class);

    private final McAcquirer          acquirer;
    private final ExecutionRepository executionRepository;
    private final ResultRepository    resultRepository;
    private final ObjectMapper        objectMapper;

    @Value("${tps.max:100}")
    private int maxTps;

    @Value("${tps.thread-pool-size:20}")
    private int threadPoolSize;

    // Active executions map
    private final Map<Long, TpsExecution> activeExecutions = new ConcurrentHashMap<>();

    public TpsEngine(McAcquirer acquirer,
                     ExecutionRepository executionRepository,
                     ResultRepository resultRepository,
                     ObjectMapper objectMapper) {
        this.acquirer            = acquirer;
        this.executionRepository = executionRepository;
        this.resultRepository    = resultRepository;
        this.objectMapper        = objectMapper;
    }

    // ── Start execution ──────────────────────────────────────

    public TpsExecution start(Execution execution, Test test) {
        Long executionId = execution.getId();
        TpsMetrics metrics = new TpsMetrics();

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        Future<?> future = executor.submit(() ->
            runExecution(execution, test, metrics, executor, scheduler));

        TpsExecution tpsExecution = new TpsExecution(executionId, test.getId(), metrics, future);
        activeExecutions.put(executionId, tpsExecution);

        log.info("[TPS] Execution {} started — test={}", executionId, test.getName());
        return tpsExecution;
    }

    // ── Stop execution ───────────────────────────────────────

    public void stop(Long executionId) {
        TpsExecution exec = activeExecutions.get(executionId);
        if (exec != null) {
            exec.stop();
            activeExecutions.remove(executionId);
            updateExecutionStatus(executionId, ExecutionStatus.STOPPED, exec.getMetrics());
            log.info("[TPS] Execution {} stopped", executionId);
        }
    }

    // ── Get metrics ──────────────────────────────────────────

    public TpsMetrics getMetrics(Long executionId) {
        TpsExecution exec = activeExecutions.get(executionId);
        return exec != null ? exec.getMetrics() : null;
    }

    public boolean isRunning(Long executionId) {
        TpsExecution exec = activeExecutions.get(executionId);
        return exec != null && exec.isRunning();
    }

    // ── Run execution ────────────────────────────────────────

    private void runExecution(Execution execution, Test test,
                               TpsMetrics metrics,
                               ExecutorService executor,
                               ScheduledExecutorService scheduler) {
        try {
            // Parse test config
            McAuthRequest baseRequest = parseConfig(test.getConfig());

            // Get TPS steps
            List<TpsStepDto> steps = getTpsSteps(test);

            if (steps.isEmpty()) {
                // Mode SIMPLE — send one transaction
                log.info("[TPS] Mode SIMPLE — sending one transaction");
                sendTransaction(baseRequest, metrics, execution.getId());
                metrics.setStatus("COMPLETED");
                updateExecutionStatus(execution.getId(), ExecutionStatus.COMPLETED, metrics);
                return;
            }

            // Mode CHARGE — send transactions at TPS rate
            log.info("[TPS] Mode CHARGE — {} steps", steps.size());

            for (int s = 0; s < steps.size(); s++) {
                TpsStepDto step = steps.get(s);
                metrics.setCurrentStep(s + 1);

                int tpsValue = Math.min(step.getTpsValue(), maxTps);
                int durationSec = step.getEndSeconds() - step.getStartSeconds();
                metrics.setCurrentTps(tpsValue);

                log.info("[TPS] Step {}/{} — {} TPS for {}s",
                        s + 1, steps.size(), tpsValue, durationSec);

                // Calculate interval between transactions
                long intervalMs = tpsValue > 0 ? 1000L / tpsValue : 1000L;

                long stepEnd = System.currentTimeMillis() + (durationSec * 1000L);
                long secondStart = System.currentTimeMillis();
                int txThisSecond = 0;

                while (System.currentTimeMillis() < stepEnd) {
                    if (Thread.currentThread().isInterrupted()) {
                        metrics.setStatus("STOPPED");
                        return;
                    }

                    // Send transaction in thread pool
                    final McAuthRequest req = cloneRequest(baseRequest);
                    executor.submit(() -> sendTransaction(req, metrics, execution.getId()));
                    txThisSecond++;

                    // Calculate actual TPS every second
                    long now = System.currentTimeMillis();
                    if (now - secondStart >= 1000) {
                        double actualTps = txThisSecond * 1000.0 / (now - secondStart);
                        metrics.recordTps(actualTps);
                        log.debug("[TPS] Step {} — actual TPS={}", s + 1, String.format("%.1f", actualTps));
                        secondStart = now;
                        txThisSecond = 0;
                    }

                    // Wait for next transaction
                    Thread.sleep(intervalMs);
                }
            }

            // Wait for remaining transactions
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            metrics.setStatus("COMPLETED");
            updateExecutionStatus(execution.getId(), ExecutionStatus.COMPLETED, metrics);
            log.info("[TPS] Execution {} completed — TX={} approved={} declined={}",
                    execution.getId(), metrics.getTxTotal(),
                    metrics.getTxApproved(), metrics.getTxDeclined());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.setStatus("STOPPED");
            updateExecutionStatus(execution.getId(), ExecutionStatus.STOPPED, metrics);
        } catch (Exception e) {
            log.error("[TPS] Execution {} error : {}", execution.getId(), e.getMessage());
            metrics.setStatus("ERROR");
            updateExecutionStatus(execution.getId(), ExecutionStatus.ERROR, metrics);
        } finally {
            activeExecutions.remove(execution.getId());
            scheduler.shutdown();
        }
    }

    // ── Send transaction ─────────────────────────────────────

    private void sendTransaction(McAuthRequest request,
                                  TpsMetrics metrics,
                                  Long executionId) {
        long start = System.currentTimeMillis();
        try {
            McAuthResult result = acquirer.authorize(request);
            long duration = System.currentTimeMillis() - start;

            metrics.record(result.isApproved(), duration);

            // Save result to database
            saveResult(executionId, result, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            metrics.record(false, duration);
            log.warn("[TPS] Transaction error : {}", e.getMessage());
        }
    }

    // ── Save result ──────────────────────────────────────────

    private void saveResult(Long executionId, McAuthResult result, long durationMs) {
        try {
            Execution execution = new Execution();
            execution.setId(executionId);

            Result r = new Result();
            r.setExecution(execution);
            r.setPanMasked(result.getDE002_PAN());
            r.setDe039(result.getDE039_RESPONSE_CODE());
            r.setDe038AuthCode(result.getDE038_AUTH_CODE());
            r.setApproved(result.isApproved());
            r.setDurationMs((int) durationMs);
            r.setRequestHex(result.getRequestHex());
            r.setResponseHex(result.getResponseHex());
            r.setExecutedAt(LocalDateTime.now());

            resultRepository.save(r);
        } catch (Exception e) {
            log.warn("[TPS] Error saving result : {}", e.getMessage());
        }
    }

    // ── Update execution status ──────────────────────────────

    private void updateExecutionStatus(Long executionId,
                                        ExecutionStatus status,
                                        TpsMetrics metrics) {
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
            });
        } catch (Exception e) {
            log.warn("[TPS] Error updating execution status : {}", e.getMessage());
        }
    }

    // ── Parse config ─────────────────────────────────────────

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

    private McAuthRequest cloneRequest(McAuthRequest original) {
        McAuthRequest clone = new McAuthRequest();
        clone.setDE002_PAN(original.getDE002_PAN());
        clone.setDE004_AMOUNT(original.getDE004_AMOUNT());
        clone.setDE003_PROCESSING_CODE(original.getDE003_PROCESSING_CODE());
        clone.setDE018_MCC(original.getDE018_MCC());
        clone.setDE022_POS_ENTRY_MODE(original.getDE022_POS_ENTRY_MODE());
        clone.setDE049_CURRENCY_CODE(original.getDE049_CURRENCY_CODE());
        clone.setDE052_PIN(original.getDE052_PIN());
        return clone;
    }

    @SuppressWarnings("unchecked")
    private List<TpsStepDto> getTpsSteps(Test test) {
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
}
