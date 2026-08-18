package com.staging.sg.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.fraud.api.FraudApi.ScoreRequest;
import com.staging.sg.fraud.domain.FraudAiPolicy;
import com.staging.sg.fraud.repository.FraudAiPolicyRepository;
import com.staging.sg.fraud.service.FraudCollectiveGraph;
import com.staging.sg.fraud.service.IndustrialRiskOrchestrator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndustrialRiskOrchestratorGovernanceTest {
    private HttpServer server;private final List<String> selectedModels=new CopyOnWriteArrayList<>();
    @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/invocations",exchange->{selectedModels.add(exchange.getRequestHeaders().getFirst("X-Fraud-Model-Name"));byte[] body="{\"predictions\":[{\"riskScore\":880,\"recommendedAction\":\"HOLD\",\"explanation\":\"collective and behavioral deviation\"}]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();}
    @AfterEach void stop(){server.stop(0);}
    @Test void activeChampionDecidesWhileChallengerRemainsShadow(){
        FraudAiPolicyRepository repository=mock(FraudAiPolicyRepository.class);FraudAiPolicy policy=FraudAiPolicy.defaults("BANK_AI","MOBILE_BANKING","champion-v3");policy.update(true,"ACTIVE","champion-v3","challenger-v4",100,.8,.7,.05,.2,"HEALTHY",true,true,350,650,800,900);when(repository.findByMemberIdAndSectorId("BANK_AI","MOBILE_BANKING")).thenReturn(Optional.of(policy));
        var orchestrator=orchestrator(repository);var result=orchestrator.evaluate("BANK_AI","subject-hash",request(),new FraudCollectiveGraph.Result(4,200,"DEVICE","ACCOUNT","MOBILE_BANKING",true));
        assertThat(result.modelScore()).isEqualTo(880);assertThat(result.challengerShadowScore()).isEqualTo(880);assertThat(result.modelVersion()).isEqualTo("champion-v3");assertThat(result.fallbackApplied()).isFalse();assertThat(selectedModels).containsExactly("champion-v3","challenger-v4");
    }
    @Test void driftedChampionFallsBackWithoutCallingModel(){
        FraudAiPolicyRepository repository=mock(FraudAiPolicyRepository.class);FraudAiPolicy policy=FraudAiPolicy.defaults("BANK_AI","MOBILE_BANKING","champion-v3");policy.update(true,"ACTIVE","champion-v3",null,0,.8,.7,.05,.2,"DRIFTED",true,true,350,650,800,900);when(repository.findByMemberIdAndSectorId("BANK_AI","MOBILE_BANKING")).thenReturn(Optional.of(policy));
        var result=orchestrator(repository).evaluate("BANK_AI","subject-hash",request(),new FraudCollectiveGraph.Result(1,0,"NONE","ACCOUNT","MOBILE_BANKING",true));
        assertThat(result.hasModel()).isFalse();assertThat(result.fallbackApplied()).isTrue();assertThat(result.modelStatus()).isEqualTo("FALLBACK:DRIFTED");assertThat(selectedModels).isEmpty();
    }
    private IndustrialRiskOrchestrator orchestrator(FraudAiPolicyRepository repository){return new IndustrialRiskOrchestrator(new ObjectMapper(),false,"http://127.0.0.1:1","fraud_scoring_v1",true,"http://127.0.0.1:"+server.getAddress().getPort()+"/invocations","fraud-risk",250000,1000,repository);}
    private ScoreRequest request(){return new ScoreRequest("ai-tx","tok-ai",1000,"MAD","MAR","5411","MOBILE_BANKING",true,true,0,"device-ai",null,"account-ai",null,null,null,"MOBILE_BANKING",Map.of());}
}
