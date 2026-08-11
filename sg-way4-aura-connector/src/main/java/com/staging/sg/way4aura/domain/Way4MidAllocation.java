package com.staging.sg.way4aura.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="way4_mid_allocation")
public class Way4MidAllocation {
    @Id private UUID id;
    @Column(name="onboarding_case_id",nullable=false,unique=true,updatable=false) private UUID onboardingCaseId;
    @Column(name="application_reg_number",nullable=false,length=96,unique=true,updatable=false) private String applicationRegNumber;
    @Column(name="mid",nullable=false,length=64,unique=true,updatable=false) private String mid;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected Way4MidAllocation(){}
    public static Way4MidAllocation allocate(UUID caseId,String reg,String mid){var v=new Way4MidAllocation();v.id=UUID.randomUUID();v.onboardingCaseId=caseId;v.applicationRegNumber=reg;v.mid=mid;v.createdAt=Instant.now();return v;}
    public String mid(){return mid;}
}
