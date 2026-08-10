package com.staging.sg.acquiring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.acquiring.integration.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class Way4ExportDispatcher {
    private final boolean enabled; private final int batchSize; private final String workerId;
    private final Way4ExportReservationService reservation; private final Way4ExportCompletionService completion;
    private final Way4ConnectorPort connector; private final ObjectMapper mapper;
    public Way4ExportDispatcher(@Value("${acquiring.way4.dispatch-enabled:false}") boolean enabled,
            @Value("${acquiring.way4.batch-size:10}") int batchSize,
            @Value("${acquiring.way4.worker-id:way4-dispatcher}") String workerId,
            Way4ExportReservationService reservation, Way4ExportCompletionService completion,
            Way4ConnectorPort connector, ObjectMapper mapper) { this.enabled=enabled; this.batchSize=batchSize;
        this.workerId=workerId; this.reservation=reservation; this.completion=completion;
        this.connector=connector; this.mapper=mapper; }
    @Scheduled(fixedDelayString="${acquiring.way4.poll-delay-ms:5000}")
    public void dispatch() {
        if(!enabled)return;
        for(Way4ExportReservationService.Reserved event:reservation.reserve(workerId,batchSize)) {
            try { Way4ExportRequest request=mapper.readValue(event.payloadJson(),Way4ExportRequest.class);
                Way4ConnectorPort.Result result=connector.generate(request); completion.completed(event.id(),workerId,result.fileId()); }
            catch(Way4ConnectorException e){ completion.failed(event.id(),workerId,e.getMessage(),e.retryable(),e.mappingBlocked()); }
            catch(Exception e){ completion.failed(event.id(),workerId,e.getClass().getSimpleName(),true,false); }
        }
    }
}
