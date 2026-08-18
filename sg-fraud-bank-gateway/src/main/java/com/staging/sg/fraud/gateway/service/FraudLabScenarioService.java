package com.staging.sg.fraud.gateway.service;

import com.staging.sg.fraud.gateway.api.IsoFraudApi.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FraudLabScenarioService {
    private final FraudPlatformClient platform;private final GatewayEvaluationService evaluation;private final boolean enabled;
    public FraudLabScenarioService(FraudPlatformClient platform,GatewayEvaluationService evaluation,@Value("${fraud-gateway.lab.enabled:false}")boolean enabled){this.platform=platform;this.evaluation=evaluation;this.enabled=enabled;}
    public LabScenarioResponse run(String authorization,LabScenarioRequest request){
        if(!enabled)throw new IllegalStateException("Synthetic laboratory scenarios are disabled");
        List<GatewayDecisionResponse> sample=new ArrayList<>();int alerts=0,challenged=0,held=0,blocked=0;
        for(int i=1;i<=request.transactionCount();i++){
            String token="lab-token-"+request.scenario().toLowerCase(Locale.ROOT)+"-"+i;
            platform.enroll(authorization,Map.of("tokenReference",token,"currency","MAD","country","MAR","customerReference","lab-customer-"+i));
            GatewayDecisionResponse result=evaluation.evaluate(authorization,event(request.scenario(),i,token));
            if(!"ALLOW".equals(result.enforcedAction()))alerts++;if("CHALLENGE".equals(result.enforcedAction()))challenged++;if("HOLD".equals(result.enforcedAction()))held++;if("BLOCK".equals(result.enforcedAction()))blocked++;
            if(sample.size()<20)sample.add(result);
        }
        return new LabScenarioResponse(request.scenario(),request.transactionCount(),alerts,challenged,held,blocked,List.copyOf(sample));
    }
    private CanonicalEventRequest event(String scenario,int i,String token){
        boolean collective="COORDINATED_GROUP".equals(scenario);boolean ecommerce="ECOMMERCE_PURCHASE".equals(scenario);boolean mobile="MOBILE_TRANSFER".equals(scenario);
        boolean atm="ATM_WITHDRAWAL".equals(scenario);String channel=atm?"ATM":ecommerce?"ECOMMERCE":mobile?"MOBILE":"POS";
        long amount=(collective||mobile)?1500000:10000;String mcc=mobile?"4829":ecommerce?"5967":"5411";
        return new CanonicalEventRequest("lab-"+scenario.toLowerCase(Locale.ROOT)+"-"+i,token,amount,"MAD","MAR",mcc,channel,!ecommerce,!collective&&!ecommerce,collective?8:1,
                collective?"lab-shared-device":"lab-device-"+i,"lab-customer-"+i,"lab-account-"+i,collective?"lab-shared-beneficiary":"lab-beneficiary-"+i,"lab-merchant",collective?"lab-shared-ip":"lab-ip-"+i,mobile?"MOBILE_BANKING":"MONETIQUE");
    }
}
