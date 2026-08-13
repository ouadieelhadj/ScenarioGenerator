package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.domain.Way4MidAllocation;
import com.staging.sg.way4aura.repository.Way4MidAllocationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class Way4MidAllocator {
    private final boolean enabled; private final String prefix; private final int numericWidth;
    private final Way4MidAllocationRepository allocations; private final EntityManager entityManager;
    public Way4MidAllocator(@Value("${way4-aura.mid-generation.enabled:false}") boolean enabled,
            @Value("${way4-aura.mid-generation.prefix:}") String prefix,
            @Value("${way4-aura.mid-generation.numeric-width:0}") int numericWidth,
            Way4MidAllocationRepository allocations,EntityManager entityManager){this.enabled=enabled;this.prefix=prefix;
        this.numericWidth=numericWidth;this.allocations=allocations;this.entityManager=entityManager;}
    public String allocate(UUID caseId,String reg){return allocations.findByOnboardingCaseId(caseId).map(Way4MidAllocation::mid).orElseGet(()->{
        // WAY4 remains authoritative while no approved range is configured.
        // In that case MerchantID is omitted from the XML; no demo MID is generated.
        if(!enabled)return null;
        if(numericWidth<1||numericWidth>18||prefix.length()+numericWidth>64)throw new AuraMappingBlockedException("MID generation format is not approved");
        long number=((Number)entityManager.createNativeQuery("select nextval('way4_mid_number_seq')").getSingleResult()).longValue();
        String digits=Long.toString(number);if(digits.length()>numericWidth)throw new AuraMappingBlockedException("Approved MID range is exhausted");
        String mid=prefix+"0".repeat(numericWidth-digits.length())+digits;
        return allocations.save(Way4MidAllocation.allocate(caseId,reg,mid)).mid();});}
}
