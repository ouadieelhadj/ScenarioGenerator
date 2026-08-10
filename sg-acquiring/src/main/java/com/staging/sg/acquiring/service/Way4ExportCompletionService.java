package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.domain.Way4ExportOutboxEvent;
import com.staging.sg.acquiring.repository.Way4ExportOutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class Way4ExportCompletionService {
    private final Way4ExportOutboxEventRepository events;

    public Way4ExportCompletionService(Way4ExportOutboxEventRepository events) {
        this.events = events;
    }

    @Transactional
    public void completed(UUID eventId, String workerId, UUID fileId) {
        event(eventId).completed(workerId, fileId, Instant.now());
    }

    @Transactional
    public void failed(UUID eventId, String workerId, String error, boolean retryable, boolean mappingBlocked) {
        event(eventId).failed(workerId, error, retryable, mappingBlocked, Instant.now());
    }

    private Way4ExportOutboxEvent event(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("WAY4 export event not found"));
    }
}
