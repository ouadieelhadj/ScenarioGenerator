package com.staging.sg.acquiring.service;

import com.staging.sg.acquiring.repository.Way4ExportOutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class Way4ExportReservationService {
    private final Way4ExportOutboxEventRepository events;

    public Way4ExportReservationService(Way4ExportOutboxEventRepository events) {
        this.events = events;
    }

    @Transactional
    public List<Reserved> reserve(String workerId, int batchSize) {
        Instant now = Instant.now();
        events.recoverExpiredLeases(now);
        return events.lockDispatchable(now, batchSize).stream().map(event -> {
            event.reserve(workerId, now, now.plus(2, ChronoUnit.MINUTES));
            return new Reserved(event.id(), event.payloadJson());
        }).toList();
    }

    public record Reserved(UUID id, String payloadJson) {}
}
