package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.FraudApi.IntegrationComponent;
import com.staging.sg.fraud.api.FraudApi.IntegrationReadiness;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class IndustrialIntegrationReadiness {
    private final boolean stream,graph;private final String topic,featureService,modelName;private final IndustrialRiskOrchestrator industrial;private final ObjectProvider<KafkaOutboxPublisher> kafka;
    public IndustrialIntegrationReadiness(@Value("${fraud.integrations.event-stream.enabled:false}")boolean stream,
            @Value("${fraud.integrations.event-stream.topic:fraud.risk-assessment-completed.v1}")String topic,
            @Value("${fraud.integrations.feature-store.enabled:false}")boolean features,
            @Value("${fraud.integrations.feature-store.feature-service:fraud_scoring_v1}")String featureService,
            @Value("${fraud.integrations.model-inference.enabled:false}")boolean model,
            @Value("${fraud.integrations.model-inference.model-name:fraud-risk}")String modelName,
            @Value("${fraud.integrations.graph.enabled:false}")boolean graph,IndustrialRiskOrchestrator industrial,ObjectProvider<KafkaOutboxPublisher> kafka){this.stream=stream;this.topic=topic;this.featureService=featureService;this.modelName=modelName;this.graph=graph;this.industrial=industrial;this.kafka=kafka;}
    public IntegrationReadiness status(){return new IntegrationReadiness(List.of(
            new IntegrationComponent("KAFKA_EVENT_STREAM",stream,stream?kafka.getIfAvailable(()->null)==null?"STARTING":kafka.getObject().status():"DISABLED",topic),
            new IntegrationComponent("FEAST_FEATURE_STORE",industrial.featureEnabled(),industrial.featureStatus(),featureService),
            new IntegrationComponent("MODEL_INFERENCE",industrial.modelEnabled(),industrial.modelStatus(),modelName),
            new IntegrationComponent("LOCAL_GRAPH_INTELLIGENCE",true,"UP","member-scoped PostgreSQL graph"),
            component("JANUSGRAPH_INTELLIGENCE",graph,"member-scoped opaque graph")),Instant.now());}
    private IntegrationComponent component(String code,boolean enabled,String binding){return new IntegrationComponent(code,enabled,enabled?"CONFIGURED_NOT_PROBED":"DISABLED",binding);}
}
