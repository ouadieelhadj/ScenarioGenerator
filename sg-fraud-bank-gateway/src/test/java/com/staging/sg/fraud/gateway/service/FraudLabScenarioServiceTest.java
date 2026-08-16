package com.staging.sg.fraud.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staging.sg.fraud.gateway.api.IsoFraudApi.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudLabScenarioServiceTest {
    @Test void labIsFailClosedOutsideExplicitLabProfile(){
        var service=new FraudLabScenarioService(mock(FraudPlatformClient.class),mock(GatewayEvaluationService.class),false);
        assertThatThrownBy(()->service.run("Bearer test",new LabScenarioRequest("ATM_WITHDRAWAL",1))).isInstanceOf(IllegalStateException.class).hasMessageContaining("disabled");
    }
    @Test void coordinatedScenarioUsesGatewayEvaluationPathAndSyntheticTokens(){
        FraudPlatformClient platform=mock(FraudPlatformClient.class);GatewayEvaluationService evaluation=mock(GatewayEvaluationService.class);
        when(evaluation.evaluate(anyString(),any())).thenAnswer(inv->{CanonicalEventRequest e=inv.getArgument(1);return new GatewayDecisionResponse(e.transactionReference(),700,"CHALLENGE","ALERT","HIGH",e.channel(),e.transactionReference());});
        var result=new FraudLabScenarioService(platform,evaluation,true).run("Bearer test",new LabScenarioRequest("COORDINATED_GROUP",100));
        assertThat(result.injected()).isEqualTo(100);assertThat(result.alerts()).isEqualTo(100);assertThat(result.sample()).hasSize(20);
        verify(platform,times(100)).enroll(eq("Bearer test"),argThat(values->values.get("tokenReference").toString().startsWith("lab-token-")));
        verify(evaluation,times(100)).evaluate(eq("Bearer test"),argThat(event->event.deviceReference().equals("lab-shared-device")&&event.tokenReference().startsWith("lab-token-")));
    }
}
