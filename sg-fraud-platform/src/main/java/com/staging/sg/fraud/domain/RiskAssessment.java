package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_risk_assessment", uniqueConstraints=@UniqueConstraint(name="uk_fraud_assessment_member_tx", columnNames={"member_id","transaction_reference"}))
public class RiskAssessment {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="sector_id",nullable=false,length=64,updatable=false) private String sectorId;
    @Column(name="transaction_reference",nullable=false,length=128,updatable=false) private String transactionReference;
    @Column(name="subject_type",nullable=false,length=32,updatable=false) private String subjectType;
    @Column(name="subject_hash",nullable=false,length=64,updatable=false) private String subjectHash;
    @Column(nullable=false) private int score;
    @Column(nullable=false,length=16) private String band;
    @Column(name="recommended_action",nullable=false,length=32) private String recommendedAction;
    @Column(name="enforced_action",nullable=false,length=32) private String enforcedAction;
    @Column(name="model_version",nullable=false,length=64) private String modelVersion;
    @Column(name="reasons_json",nullable=false,length=4000) private String reasonsJson;
    @Column(name="collective_group_size",nullable=false) private int collectiveGroupSize;
    @Column(name="collective_risk_score",nullable=false) private int collectiveRiskScore;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected RiskAssessment() {}
    public static RiskAssessment create(String memberId,String sectorId,String tx,String subjectType,String subjectHash,int score,String band,String recommended,String enforced,String modelVersion,String reasonsJson,int groupSize,int collectiveScore){
        RiskAssessment r=new RiskAssessment(); r.id=UUID.randomUUID(); r.memberId=memberId; r.sectorId=sectorId; r.transactionReference=tx; r.subjectType=subjectType;r.subjectHash=subjectHash;
        r.score=score; r.band=band; r.recommendedAction=recommended; r.enforcedAction=enforced; r.modelVersion=modelVersion;
        r.reasonsJson=reasonsJson; r.collectiveGroupSize=groupSize; r.collectiveRiskScore=collectiveScore; r.createdAt=Instant.now(); return r;
    }
    public UUID id(){return id;} public String memberId(){return memberId;} public String sectorId(){return sectorId;} public String transactionReference(){return transactionReference;}
    public String subjectType(){return subjectType;}public String subjectHash(){return subjectHash;}
    public int score(){return score;} public String band(){return band;} public String recommendedAction(){return recommendedAction;}
    public String enforcedAction(){return enforcedAction;} public String modelVersion(){return modelVersion;} public String reasonsJson(){return reasonsJson;}
    public int collectiveGroupSize(){return collectiveGroupSize;} public int collectiveRiskScore(){return collectiveRiskScore;} public Instant createdAt(){return createdAt;}
}
