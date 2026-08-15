package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_control_candidate")
public class ControlCandidate {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false,length=24) private String status;
    @Column(nullable=false) private double precisionValue;
    @Column(nullable=false) private double recallValue;
    @Column(name="false_positive_rate",nullable=false) private double falsePositiveRate;
    @Column(name="governance_decision",nullable=false,length=32) private String governanceDecision;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected ControlCandidate(){}
    public static ControlCandidate backtested(String memberId,String name,double precision,double recall,double fpr,String decision){ControlCandidate c=new ControlCandidate();c.id=UUID.randomUUID();c.memberId=memberId;c.name=name;c.status="BACKTESTED";c.precisionValue=precision;c.recallValue=recall;c.falsePositiveRate=fpr;c.governanceDecision=decision;c.createdAt=Instant.now();return c;}
    public UUID id(){return id;} public String status(){return status;} public double precision(){return precisionValue;} public double recall(){return recallValue;} public double falsePositiveRate(){return falsePositiveRate;} public String governanceDecision(){return governanceDecision;} public Instant createdAt(){return createdAt;}
}
