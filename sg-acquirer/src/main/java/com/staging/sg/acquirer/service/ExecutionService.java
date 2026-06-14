package com.staging.sg.acquirer.service;

import com.staging.sg.acquirer.tps.TpsEngine;
import com.staging.sg.acquirer.tps.TpsExecution;
import com.staging.sg.acquirer.tps.TpsMetrics;
import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.ExecutionRepository;
import com.staging.sg.common.repository.ResultRepository;
import com.staging.sg.common.repository.TestRepository;
import com.staging.sg.common.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final TpsEngine           tpsEngine;
    private final ExecutionRepository executionRepository;
    private final ResultRepository    resultRepository;
    private final TestRepository      testRepository;
    private final UserRepository      userRepository;

    public ExecutionService(TpsEngine tpsEngine,
                            ExecutionRepository executionRepository,
                            ResultRepository resultRepository,
                            TestRepository testRepository,
                            UserRepository userRepository) {
        this.tpsEngine           = tpsEngine;
        this.executionRepository = executionRepository;
        this.resultRepository    = resultRepository;
        this.testRepository      = testRepository;
        this.userRepository      = userRepository;
    }

    // ── Start execution ──────────────────────────────────────

    @Transactional
    public Map<String, Object> start(Long testId, String userLogin, String mode) {
        // Load test
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found : " + testId));

        // Load user
        User user = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new RuntimeException("User not found : " + userLogin));

        // Determine mode
        ExecutionMode execMode = "CHARGE".equalsIgnoreCase(mode)
                ? ExecutionMode.CHARGE : ExecutionMode.SIMPLE;

        // Create execution record
        Execution execution = new Execution();
        execution.setTest(test);
        execution.setUser(user);
        execution.setMode(execMode);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedAt(LocalDateTime.now());
        execution = executionRepository.save(execution);

        log.info("[EXECUTION] Started — id={} test={} user={} mode={}",
                execution.getId(), test.getName(), userLogin, execMode);

        // Start TPS engine
        TpsExecution tpsExecution = tpsEngine.start(execution, test);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", execution.getId());
        result.put("testId", testId);
        result.put("testName", test.getName());
        result.put("mode", execMode);
        result.put("status", "RUNNING");
        result.put("startedAt", execution.getStartedAt());
        return result;
    }

    // ── Stop execution ───────────────────────────────────────

    public Map<String, Object> stop(Long executionId) {
        tpsEngine.stop(executionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);
        result.put("status", "STOPPED");
        result.put("stoppedAt", LocalDateTime.now());
        return result;
    }

    // ── Get status ───────────────────────────────────────────

    public Map<String, Object> getStatus(Long executionId) {
        TpsMetrics metrics = tpsEngine.getMetrics(executionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);

        if (metrics != null) {
            result.put("status",           metrics.getStatus());
            result.put("txTotal",          metrics.getTxTotal());
            result.put("txApproved",       metrics.getTxApproved());
            result.put("txDeclined",       metrics.getTxDeclined());
            result.put("currentTps",       metrics.getCurrentTps());
            result.put("currentStep",      metrics.getCurrentStep());
            result.put("avgTps",           String.format("%.1f", metrics.getAvgTps()));
            result.put("avgResponseMs",    String.format("%.0f", metrics.getAvgResponseMs()));
            result.put("minResponseMs",    metrics.getMinResponseMs());
            result.put("maxResponseMs",    metrics.getMaxResponseMs());
            result.put("p95ResponseMs",    String.format("%.0f", metrics.getP95ResponseMs()));
            result.put("p99ResponseMs",    String.format("%.0f", metrics.getP99ResponseMs()));
            result.put("elapsedSeconds",   String.format("%.1f", metrics.getElapsedSeconds()));
            result.put("running",          tpsEngine.isRunning(executionId));
        } else {
            // Load from database
            executionRepository.findById(executionId).ifPresent(exec -> {
                result.put("status",      exec.getStatus());
                result.put("txTotal",     exec.getTxTotal());
                result.put("txApproved",  exec.getTxApproved());
                result.put("txDeclined",  exec.getTxDeclined());
                result.put("avgTps",      exec.getTpsActualAvg());
                result.put("avgResponseMs", exec.getResponseTimeAvg());
                result.put("running",     false);
            });
        }
        return result;
    }

    // ── Get history ──────────────────────────────────────────

    public List<Execution> getHistory(String userLogin) {
        User user = userRepository.findByLogin(userLogin)
                .orElseThrow(() -> new RuntimeException("User not found : " + userLogin));
        return executionRepository.findByUserIdOrderByStartedAtDesc(user.getId());
    }

    public List<Execution> getAllHistory() {
        return executionRepository.findAllByOrderByStartedAtDesc();
    }
}
