package com.staging.sg.fraud.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.fraud.gateway.api.OmnichannelApi;
import com.staging.sg.fraud.gateway.api.OmnichannelApi.UniversalTransactionRequest;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmnichannelGatewayServiceTest {
    @Test void normalizesEveryCommercialDomainWithoutChangingTheDecisionBoundary(){
        FraudPlatformClient client=mock(FraudPlatformClient.class);GatewayEvaluationService evaluation=new GatewayEvaluationService(client);
        OmnichannelGatewayService service=new OmnichannelGatewayService(evaluation);ObjectMapper json=new ObjectMapper();
        when(client.score(anyString(),anyMap())).thenReturn(json.createObjectNode().put("score",700).put("recommendedAction","CHALLENGE").put("enforcedAction","ALERT").put("band","HIGH"));
        for(String domain:OmnichannelApi.DOMAINS){
            var request=new UniversalTransactionRequest("TX-"+domain,domain,"REST","AUTHORIZATION","TOKEN-"+domain,null,null,null,null,null,null,100,"MAD","MAR","5411",false,true,1,Instant.now(),Map.of());
            var result=service.evaluate("Bearer test",request);assertThat(result.transactionReference()).isEqualTo("TX-"+domain);assertThat(result.enforcedAction()).isEqualTo("ALERT");
        }
        verify(client,times(OmnichannelApi.DOMAINS.size())).score(eq("Bearer test"),anyMap());
    }
}
