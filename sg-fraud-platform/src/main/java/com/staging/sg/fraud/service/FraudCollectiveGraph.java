package com.staging.sg.fraud.service;

import com.staging.sg.fraud.api.FraudApi.ScoreRequest;
import com.staging.sg.fraud.domain.FraudEntityLink;
import com.staging.sg.fraud.repository.FraudEntityLinkRepository;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FraudCollectiveGraph {
    private final FraudEntityLinkRepository links; private final ReferenceProtector protector;
    public FraudCollectiveGraph(FraudEntityLinkRepository links,ReferenceProtector protector){this.links=links;this.protector=protector;}
    public Result observeAndEvaluate(String memberId,String tokenHash,ScoreRequest request){
        Map<String,String> refs=new LinkedHashMap<>(); put(refs,"DEVICE",request.deviceReference());put(refs,"CUSTOMER",request.customerReference());
        put(refs,"ACCOUNT",request.accountReference());put(refs,"BENEFICIARY",request.beneficiaryReference());put(refs,"MERCHANT",request.merchantReference());put(refs,"IP",request.ipReference());
        int strongest=1;String strongestType="NONE";
        for(var entry:refs.entrySet()){
            String entityHash=protector.hash(entry.getValue());
            Optional<FraudEntityLink> existing=links.findByMemberIdAndSubjectHashAndEntityTypeAndEntityHash(memberId,tokenHash,entry.getKey(),entityHash);
            if(existing.isPresent()){FraudEntityLink link=existing.get();link.observe();links.saveAndFlush(link);}
            else links.saveAndFlush(FraudEntityLink.first(memberId,tokenHash,entry.getKey(),entityHash));
            int size=(int)links.countDistinctByMemberIdAndEntityTypeAndEntityHash(memberId,entry.getKey(),entityHash);
            if(size>strongest){strongest=size;strongestType=entry.getKey();}
        }
        int score=strongest>=3?Math.min(700,180+(strongest-3)*12):0;
        return new Result(strongest,score,strongestType);
    }
    private void put(Map<String,String> refs,String type,String value){if(value!=null&&!value.isBlank())refs.put(type,value);}
    public record Result(int groupSize,int contribution,String sharedEntityType){}
}
