package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.FraudApi.ScoreRequest;
import com.staging.sg.fraud.domain.FraudEntityLink;
import com.staging.sg.fraud.domain.FraudGraphPolicy;
import com.staging.sg.fraud.repository.FraudEntityLinkRepository;
import com.staging.sg.fraud.repository.FraudGraphPolicyRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class FraudCollectiveGraph {
    private final FraudEntityLinkRepository links; private final FraudGraphPolicyRepository policies;private final ReferenceProtector protector;
    public FraudCollectiveGraph(FraudEntityLinkRepository links,FraudGraphPolicyRepository policies,ReferenceProtector protector){this.links=links;this.policies=policies;this.protector=protector;}
    public Result observeAndEvaluate(String memberId,FraudSubjectIdentity subject,String subjectHash,ScoreRequest request){
        FraudGraphPolicy policy=policies.findByMemberIdAndSectorId(memberId,subject.sectorId()).orElseGet(()->FraudGraphPolicy.defaults(memberId,subject.sectorId()));
        if(!policy.enabled())return new Result(1,0,"DISABLED",subject.subjectType(),subject.sectorId(),false);
        Map<String,String> refs=new LinkedHashMap<>(); put(refs,"DEVICE",request.deviceReference());put(refs,"CUSTOMER",request.customerReference());
        put(refs,"ACCOUNT",request.accountReference());put(refs,"BENEFICIARY",request.beneficiaryReference());put(refs,"MERCHANT",request.merchantReference());put(refs,"IP",request.ipReference());
        refs.keySet().removeIf(type->!policy.allowedTypes().contains(type));int strongest=1;String strongestType="NONE";
        Instant cutoff=Instant.now().minusSeconds(policy.observationWindowMinutes()*60L);
        for(var entry:refs.entrySet()){
            String entityHash=protector.hash(entry.getValue());
            Optional<FraudEntityLink> existing=links.findByMemberIdAndSectorIdAndSubjectTypeAndSubjectHashAndEntityTypeAndEntityHash(memberId,subject.sectorId(),subject.subjectType(),subjectHash,entry.getKey(),entityHash);
            if(existing.isPresent()){FraudEntityLink link=existing.get();link.observe();links.saveAndFlush(link);}
            else links.saveAndFlush(FraudEntityLink.first(memberId,subject.sectorId(),subject.subjectType(),subjectHash,request.channel(),entry.getKey(),entityHash));
            int size=(int)(policy.crossSectorEnabled()?links.countDistinctSubjectsAcrossSectors(memberId,entry.getKey(),entityHash,cutoff,policy.minimumObservations()):links.countDistinctSubjectsInSector(memberId,subject.sectorId(),entry.getKey(),entityHash,cutoff,policy.minimumObservations()));
            if(size>strongest){strongest=size;strongestType=entry.getKey();}
        }
        int score=strongest>=policy.minimumGroupSize()?Math.min(policy.maximumScore(),policy.baseScore()+(strongest-policy.minimumGroupSize())*policy.scorePerAdditionalSubject()):0;
        return new Result(strongest,score,strongestType,subject.subjectType(),subject.sectorId(),policy.crossSectorEnabled());
    }
    private void put(Map<String,String> refs,String type,String value){if(value!=null&&!value.isBlank())refs.put(type,value);}
    public record Result(int groupSize,int contribution,String sharedEntityType,String subjectType,String sectorId,boolean crossSector){}
}
