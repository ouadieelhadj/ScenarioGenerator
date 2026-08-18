package com.staging.sg.fraud.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.fraud.gateway.api.IsoFraudApi.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GatewayEvaluationService {
    private final FraudPlatformClient platform;
    public GatewayEvaluationService(FraudPlatformClient platform){this.platform=platform;}
    public GatewayDecisionResponse evaluate(String authorization,CanonicalEventRequest request){
        Map<String,Object> canonical=new LinkedHashMap<>();canonical.put("transactionReference",request.transactionReference());canonical.put("tokenReference",request.tokenReference());
        canonical.put("amountMinor",request.amountMinor());canonical.put("currency",request.currency());canonical.put("country",request.country());canonical.put("mcc",request.mcc());
        canonical.put("channel",request.channel());canonical.put("cardPresent",request.cardPresent());canonical.put("strongAuthentication",request.strongAuthentication());canonical.put("attemptsLastHour",request.attemptsLastHour());
        put(canonical,"deviceReference",request.deviceReference());put(canonical,"customerReference",request.customerReference());put(canonical,"accountReference",request.accountReference());
        put(canonical,"beneficiaryReference",request.beneficiaryReference());put(canonical,"merchantReference",request.merchantReference());put(canonical,"ipReference",request.ipReference());put(canonical,"sector",request.sector());
        canonical.put("observedSignals",request.observedSignals());
        return response(request.transactionReference(),request.channel(),platform.score(authorization,canonical));
    }
    public GatewayDecisionResponse response(String tx,String channel,JsonNode score){return new GatewayDecisionResponse(tx,score.path("score").asInt(),score.path("recommendedAction").asText(),score.path("enforcedAction").asText(),score.path("band").asText(),channel,tx);}
    private void put(Map<String,Object> values,String key,String value){if(value!=null&&!value.isBlank())values.put(key,value);}
}
