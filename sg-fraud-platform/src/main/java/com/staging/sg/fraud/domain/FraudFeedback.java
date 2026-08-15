package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_feedback")
public class FraudFeedback {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="alert_id",nullable=false,updatable=false) private UUID alertId;
    @Column(nullable=false,length=24) private String outcome;
    @Column(length=500) private String comment;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected FraudFeedback() {}
    public static FraudFeedback create(String memberId,UUID alertId,String outcome,String comment){FraudFeedback f=new FraudFeedback();f.id=UUID.randomUUID();f.memberId=memberId;f.alertId=alertId;f.outcome=outcome;f.comment=comment;f.createdAt=Instant.now();return f;}
    public UUID id(){return id;} public String outcome(){return outcome;} public Instant createdAt(){return createdAt;}
}
