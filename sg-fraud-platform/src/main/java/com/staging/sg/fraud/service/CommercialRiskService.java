package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.CommercialRiskApi.*;
import com.staging.sg.fraud.api.FraudApi.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CommercialRiskService {
    private final FraudService fraud;
    public CommercialRiskService(FraudService fraud){this.fraud=fraud;}

    public RiskScoreResponse score(String memberId,RiskScoreRequest request){
        ScoreRequest internal=new ScoreRequest(request.transactionId(),request.protectedInstrumentReference(),request.amount(),
                request.currency(),normalizeCountry(request.country()),request.mcc()==null?"0000":request.mcc(),request.channel(),
                request.cardPresent(),request.strongAuthentication(),request.attemptsLastHour(),request.deviceId(),request.customerId(),
                request.accountId(),request.beneficiaryId(),request.merchantId(),request.ip(),sector(request.channel()),request.signals());
        return response(request.transactionId(),fraud.score(memberId,internal));
    }

    private RiskScoreResponse response(String transactionId,ScoreResponse score){
        int publicScore=Math.max(0,Math.min(100,(int)Math.round(score.score()/10.0d)));
        double confidence=Math.round((score.score()/1000.0d)*100.0d)/100.0d;
        List<String> reasons=score.reasons().stream().map(RiskReason::code).toList();
        return new RiskScoreResponse(score.assessmentId(),transactionId,publicScore,score.recommendedAction(),score.enforcedAction(),
                score.band(),fraudType(reasons),confidence,reasons,score.modelVersion(),score.assessedAt());
    }
    private String fraudType(List<String> reasons){
        if(reasons.stream().anyMatch(Set.of("NEW_DEVICE","NEW_LOCATION","BENEFICIARY_CHANGED","SESSION_RISK")::contains))return "ACCOUNT_TAKEOVER";
        if(reasons.contains("COLLECTIVE_PATTERN"))return "FRAUD_RING";
        if(reasons.contains("ML_ANOMALY")||reasons.contains("BEHAVIORAL_DEVIATION"))return "BEHAVIORAL_ANOMALY";
        return reasons.size()==1&&reasons.contains("BASELINE")?"NONE":"TRANSACTION_FRAUD";
    }
    private String sector(String channel){String value=channel.toUpperCase(Locale.ROOT);return value.contains("MOBILE")?"MOBILE_BANKING":value.contains("INTERNET")?"INTERNET_BANKING":value.contains("3DS")?"3DS":"PAYMENTS";}
    private String normalizeCountry(String country){return country.length()==2&&"MA".equals(country)?"MAR":country;}
}
