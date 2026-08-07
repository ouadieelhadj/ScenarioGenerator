package com.staging.sg.switchlab.bff.service;

import com.staging.sg.switchlab.contracts.SwitchLabPosExecution;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

@Service
public class SwitchLabPosExecutionService {
    private static final int CAPACITY = 200;
    private final ConcurrentLinkedDeque<SwitchLabPosExecution> executions = new ConcurrentLinkedDeque<>();

    public SwitchLabPosExecution save(SwitchLabPosExecution execution) {
        executions.addFirst(execution);
        while (executions.size() > CAPACITY) executions.pollLast();
        return execution;
    }

    public List<SwitchLabPosExecution> latest(int requestedLimit) {
        return executions.stream().limit(Math.max(1, Math.min(requestedLimit, 100))).toList();
    }
}
