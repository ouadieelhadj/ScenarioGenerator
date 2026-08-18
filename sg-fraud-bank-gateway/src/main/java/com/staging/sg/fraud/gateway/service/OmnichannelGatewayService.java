package com.staging.sg.fraud.gateway.service;

import com.staging.sg.fraud.gateway.api.IsoFraudApi.*;
import com.staging.sg.fraud.gateway.api.OmnichannelApi.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OmnichannelGatewayService {
    private final GatewayEvaluationService evaluation;
    public OmnichannelGatewayService(GatewayEvaluationService evaluation){this.evaluation=evaluation;}

    public GatewayDecisionResponse evaluate(String authorization,UniversalTransactionRequest request){
        return evaluation.evaluate(authorization,toCanonical(request));
    }
    public BatchEvaluationResponse evaluateBatch(String authorization,BatchEvaluationRequest request){
        List<GatewayDecisionResponse> results=request.transactions().stream().map(tx->evaluate(authorization,tx)).toList();
        int alerts=(int)results.stream().filter(r->!"ALLOW".equals(r.recommendedAction())).count();
        return new BatchEvaluationResponse(request.transactions().size(),results.size(),alerts,results);
    }
    CanonicalEventRequest toCanonical(UniversalTransactionRequest request){
        String mcc=request.mcc()==null?"0000":request.mcc();
        return new CanonicalEventRequest(request.transactionId(),request.instrumentToken(),request.amountMinor(),request.currency(),
                request.country(),mcc,request.domain(),request.cardPresent(),request.strongAuthentication(),request.attemptsLastHour(),
                request.deviceToken(),request.customerToken(),request.accountToken(),request.beneficiaryToken(),request.merchantToken(),
                request.ipToken(),request.domain(),request.signals());
    }
}
