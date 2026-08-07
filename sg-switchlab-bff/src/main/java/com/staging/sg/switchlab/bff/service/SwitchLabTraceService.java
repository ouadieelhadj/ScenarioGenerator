package com.staging.sg.switchlab.bff.service;

import com.staging.sg.switchlab.contracts.SwitchLabTraceEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

@Service
public class SwitchLabTraceService {
    private static final int CAPACITY = 500;
    private final ConcurrentLinkedDeque<SwitchLabTraceEvent> events = new ConcurrentLinkedDeque<>();

    public void record(String correlationId, String level, String component, String message) {
        events.addFirst(new SwitchLabTraceEvent(UUID.randomUUID().toString(), Instant.now(), correlationId,
                "HTTP", level, component, message));
        while (events.size() > CAPACITY) events.pollLast();
    }

    public List<SwitchLabTraceEvent> latest(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return events.stream().limit(limit).toList();
    }
}
