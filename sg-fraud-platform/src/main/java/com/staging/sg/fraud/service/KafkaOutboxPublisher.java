package com.staging.sg.fraud.service;

import com.staging.sg.fraud.domain.FraudEventOutbox;
import com.staging.sg.fraud.repository.FraudEventOutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name="fraud.integrations.event-stream.enabled",havingValue="true")
public class KafkaOutboxPublisher {
    private final FraudEventOutboxRepository outbox;private final KafkaTemplate<String,String> kafka;private final FraudEventRouteResolver routes;
    private final int maxAttempts;private final long sendTimeoutMs;private final AtomicReference<String> status=new AtomicReference<>("READY");
    public KafkaOutboxPublisher(FraudEventOutboxRepository outbox,KafkaTemplate<String,String> kafka,FraudEventRouteResolver routes,
            @Value("${fraud.integrations.event-stream.max-attempts:8}")int maxAttempts,
            @Value("${fraud.integrations.event-stream.send-timeout-ms:5000}")long sendTimeoutMs){
        this.outbox=outbox;this.kafka=kafka;this.routes=routes;this.maxAttempts=Math.max(1,maxAttempts);this.sendTimeoutMs=Math.max(100,sendTimeoutMs);
    }
    @Scheduled(fixedDelayString="${fraud.integrations.event-stream.poll-delay-ms:1000}")
    @Transactional
    public void publishDue(){
        Instant now=Instant.now();
        for(FraudEventOutbox event:outbox.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("PENDING",now)){
            try{var route=routes.resolve(event);kafka.send(record(event,route)).get(sendTimeoutMs,TimeUnit.MILLISECONDS);event.published();status.set("UP");}
            catch(Exception failure){event.retry(now,maxAttempts);status.set("DEGRADED:"+failure.getClass().getSimpleName());}
        }
    }
    public String status(){return status.get();}
    private ProducerRecord<String,String> record(FraudEventOutbox event,FraudEventRouteResolver.RouteDecision route){
        ProducerRecord<String,String> record=new ProducerRecord<>(route.topic(),event.memberId()+":"+event.sectorId()+":"+event.aggregateId(),event.payloadJson());
        header(record,"fraud-schema-version",route.schemaVersion());header(record,"fraud-member-id",event.memberId());
        header(record,"fraud-sector-id",event.sectorId());header(record,"fraud-event-type",event.eventType());
        header(record,"fraud-route-priority",Integer.toString(route.priority()));header(record,"fraud-retention-class",route.retentionClass());
        return record;
    }
    private void header(ProducerRecord<String,String> record,String name,String value){record.headers().add(new RecordHeader(name,value.getBytes(StandardCharsets.UTF_8)));}
}
