package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_risk_assessment", uniqueConstraints=@UniqueConstraint(name="uk_fraud_assessment_member_tx", columnNames={"member_id","transaction_reference"}))
public class RiskAssessment {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="transaction_reference",nullable=false,length=128,updatable=false) private String transactionReference;
    @Column(name="token_hash",nullable=false,length=64,updatable=false) private String tokenHash;
    @Column(nullable=false) private int score;
    @Column(nullable=false,length=16) private String band;
    @Column(name="recommended_action",nullable=false,length=32) private String recommendedAction;
    @Column(name="enforced_action",nullable=false,length=32) private String enforcedAction;
    @Column(name="model_version",nullable=false,length=64) private String modelVersion;
    @Column(name="reasons_json",nullable=false,length=4000) private String reasonsJson;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected RiskAssessment() {}
    public static RiskAssessment create(String memberId,String tx,String tokenHash,int score,String band,String recommended,String reasonsJson){
        RiskAssessment r=new RiskAssessment(); r.id=UUID.randomUUID(); r.memberId=memberId; r.transactionReference=tx; r.tokenHash=tokenHash;
        r.score=score; r.band=band; r.recommendedAction=recommended; r.enforcedAction="NO_BLOCK_ALERT_ONLY"; r.modelVersion="baseline-explainable-v1"; r.reasonsJson=reasonsJson; r.createdAt=Instant.now(); return r;
    }
    public UUID id(){return id;} public String memberId(){return memberId;} public String transactionReference(){return transactionReference;}
    public int score(){return score;} public String band(){return band;} public String recommendedAction(){return recommendedAction;}
    public String enforcedAction(){return enforcedAction;} public String modelVersion(){return modelVersion;} public String reasonsJson(){return reasonsJson;} public Instant createdAt(){return createdAt;}
}
