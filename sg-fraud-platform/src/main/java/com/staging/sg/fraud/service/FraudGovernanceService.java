package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.FraudGovernanceApi.*;
import com.staging.sg.fraud.domain.*;
import com.staging.sg.fraud.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class FraudGovernanceService {
    private final FraudGraphPolicyRepository graphPolicies;private final FraudAiPolicyRepository aiPolicies;private final String defaultModel;
    public FraudGovernanceService(FraudGraphPolicyRepository graphPolicies,FraudAiPolicyRepository aiPolicies,
            @Value("${fraud.integrations.model-inference.model-name:fraud-risk}")String defaultModel){this.graphPolicies=graphPolicies;this.aiPolicies=aiPolicies;this.defaultModel=defaultModel;}
    @Transactional(readOnly=true)public GraphPolicyResponse graph(String memberId,String sector){String s=sector(sector);return graphResponse(graphPolicies.findByMemberIdAndSectorId(memberId,s).orElseGet(()->FraudGraphPolicy.defaults(memberId,s)));}
    @Transactional public GraphPolicyResponse updateGraph(String memberId,String sector,GraphPolicyRequest request){String s=sector(sector);String types=normalizeTypes(request.allowedEntityTypes());if(types.isBlank())throw new IllegalArgumentException("At least one graph entity type is required");if(request.maximumScore()<request.baseScore())throw new IllegalArgumentException("maximumScore must be greater than or equal to baseScore");FraudGraphPolicy p=graphPolicies.findByMemberIdAndSectorId(memberId,s).orElseGet(()->FraudGraphPolicy.defaults(memberId,s));p.update(request.enabled(),request.crossSectorEnabled(),types,request.minimumGroupSize(),request.baseScore(),request.scorePerAdditionalSubject(),request.maximumScore(),request.observationWindowMinutes(),request.minimumObservations(),request.maximumHops());return graphResponse(graphPolicies.save(p));}
    @Transactional(readOnly=true)public AiPolicyResponse ai(String memberId,String sector){String s=sector(sector);return aiResponse(aiPolicies.findByMemberIdAndSectorId(memberId,s).orElseGet(()->FraudAiPolicy.defaults(memberId,s,defaultModel)));}
    @Transactional public AiPolicyResponse updateAi(String memberId,String sector,AiPolicyRequest r){String s=sector(sector);if(!(r.alertThreshold()<=r.challengeThreshold()&&r.challengeThreshold()<=r.holdThreshold()&&r.holdThreshold()<=r.blockThreshold()))throw new IllegalArgumentException("AI decision thresholds must be ordered");if(r.challengerTrafficPercent()>0&&(r.challengerModel()==null||r.challengerModel().isBlank()))throw new IllegalArgumentException("A challenger model is required when challenger traffic is enabled");if(!r.analystApprovalRequired())throw new IllegalArgumentException("Analyst approval is mandatory for AI governance");FraudAiPolicy p=aiPolicies.findByMemberIdAndSectorId(memberId,s).orElseGet(()->FraudAiPolicy.defaults(memberId,s,defaultModel));p.update(r.enabled(),r.governanceMode(),r.championModel(),blankToNull(r.challengerModel()),r.challengerTrafficPercent(),r.minimumPrecision(),r.minimumRecall(),r.maximumFalsePositiveRate(),r.driftThreshold(),r.driftStatus(),r.explainabilityRequired(),true,r.alertThreshold(),r.challengeThreshold(),r.holdThreshold(),r.blockThreshold());return aiResponse(aiPolicies.save(p));}
    private String sector(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{2,64}"))throw new IllegalArgumentException("Invalid sector");return value.toUpperCase();}
    private String normalizeTypes(String value){return String.join(",",Arrays.stream(value.split(",")).map(String::trim).map(String::toUpperCase).filter(v->v.matches("[A-Z_]{2,32}")).distinct().sorted().toList());}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value;}
    private GraphPolicyResponse graphResponse(FraudGraphPolicy p){return new GraphPolicyResponse(p.memberId(),p.sectorId(),p.enabled(),p.crossSectorEnabled(),p.allowedEntityTypes(),p.minimumGroupSize(),p.baseScore(),p.scorePerAdditionalSubject(),p.maximumScore(),p.observationWindowMinutes(),p.minimumObservations(),p.maximumHops(),p.updatedAt());}
    private AiPolicyResponse aiResponse(FraudAiPolicy p){return new AiPolicyResponse(p.memberId(),p.sectorId(),p.enabled(),p.governanceMode(),p.championModel(),p.challengerModel(),p.challengerTrafficPercent(),p.minimumPrecision(),p.minimumRecall(),p.maximumFalsePositiveRate(),p.driftThreshold(),p.driftStatus(),p.explainabilityRequired(),p.analystApprovalRequired(),p.alertThreshold(),p.challengeThreshold(),p.holdThreshold(),p.blockThreshold(),p.updatedAt());}
}
