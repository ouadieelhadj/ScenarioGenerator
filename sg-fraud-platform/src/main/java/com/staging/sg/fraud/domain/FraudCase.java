package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_case",uniqueConstraints=@UniqueConstraint(name="uk_fraud_case_member_alert",columnNames={"member_id","alert_id"}))
public class FraudCase {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="alert_id",nullable=false,updatable=false) private UUID alertId;
    @Column(nullable=false,length=160) private String title;
    @Column(nullable=false,length=24) private String status;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected FraudCase(){}
    public static FraudCase open(String memberId,UUID alertId,String title){FraudCase c=new FraudCase();c.id=UUID.randomUUID();c.memberId=memberId;c.alertId=alertId;c.title=title;c.status="OPEN";c.createdAt=Instant.now();return c;}
    public UUID id(){return id;} public UUID alertId(){return alertId;} public String title(){return title;} public String status(){return status;} public Instant createdAt(){return createdAt;}
}
