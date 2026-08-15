package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_alert", indexes=@Index(name="ix_fraud_alert_member_created",columnList="member_id,created_at"))
public class FraudAlert {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="assessment_id",nullable=false,unique=true,updatable=false) private UUID assessmentId;
    @Column(name="transaction_reference",nullable=false,length=128,updatable=false) private String transactionReference;
    @Column(nullable=false) private int score;
    @Column(nullable=false,length=16) private String band;
    @Column(nullable=false,length=24) private String status;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected FraudAlert() {}
    public static FraudAlert open(String memberId,RiskAssessment assessment){ FraudAlert a=new FraudAlert(); a.id=UUID.randomUUID(); a.memberId=memberId; a.assessmentId=assessment.id(); a.transactionReference=assessment.transactionReference(); a.score=assessment.score(); a.band=assessment.band(); a.status="OPEN"; a.createdAt=Instant.now(); return a; }
    public UUID id(){return id;} public String memberId(){return memberId;} public String transactionReference(){return transactionReference;}
    public int score(){return score;} public String band(){return band;} public String status(){return status;} public Instant createdAt(){return createdAt;}
}
