package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.FraudApi.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FraudScoringEngine {
    private final long highAmountMinor;
    public FraudScoringEngine(@Value("${fraud.scoring.high-amount-minor:1000000}") long highAmountMinor) { this.highAmountMinor=highAmountMinor; }
    public Result score(ScoreRequest request,FraudCollectiveGraph.Result collective) {
        List<RiskReason> reasons=new ArrayList<>();
        add(reasons, request.amountMinor() >= highAmountMinor, "HIGH_AMOUNT", 260, "Montant supérieur au seuil configuré");
        add(reasons, request.attemptsLastHour() >= 5, "HIGH_VELOCITY", Math.min(300, request.attemptsLastHour()*25), "Vélocité inhabituelle sur la dernière heure");
        add(reasons, !request.cardPresent(), "CARD_NOT_PRESENT", 110, "Transaction sans présence physique de la carte");
        add(reasons, !request.strongAuthentication(), "NO_STRONG_AUTH", 170, "Authentification forte non observée");
        add(reasons, Set.of("4829","5967","7995").contains(request.mcc()), "ELEVATED_MCC", 180, "Catégorie commerçant à vigilance renforcée");
        add(reasons, collective.contribution()>0, "COLLECTIVE_PATTERN", collective.contribution(),
                "Groupe coordonné détecté via "+collective.sharedEntityType()+" ("+collective.groupSize()+" instruments liés)");
        int score=Math.min(1000,reasons.stream().mapToInt(RiskReason::contribution).sum());
        String band=score>=800?"CRITICAL":score>=650?"HIGH":score>=350?"MEDIUM":"LOW";
        String action=score>=900?"BLOCK":score>=800?"HOLD":score>=650?"CHALLENGE":score>=350?"ALERT":"ALLOW";
        if(reasons.isEmpty()) reasons.add(new RiskReason("BASELINE",0,"Aucun signal de risque déterministe observé"));
        return new Result(score,band,action,List.copyOf(reasons),collective.groupSize(),collective.contribution());
    }
    private void add(List<RiskReason> reasons,boolean condition,String code,int contribution,String explanation){if(condition)reasons.add(new RiskReason(code,contribution,explanation));}
    public record Result(int score,String band,String recommendedAction,List<RiskReason> reasons,int groupSize,int collectiveScore){}
}
