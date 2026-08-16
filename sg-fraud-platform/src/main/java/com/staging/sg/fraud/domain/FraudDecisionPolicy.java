package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="fraud_decision_policy", uniqueConstraints=@UniqueConstraint(name="uk_fraud_policy_member", columnNames="member_id"))
public class FraudDecisionPolicy {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(nullable=false,length=24) private String mode;
    @Column(name="challenge_enabled",nullable=false) private boolean challengeEnabled;
    @Column(name="hold_enabled",nullable=false) private boolean holdEnabled;
    @Column(name="block_enabled",nullable=false) private boolean blockEnabled;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected FraudDecisionPolicy() {}
    public static FraudDecisionPolicy create(String memberId){FraudDecisionPolicy p=new FraudDecisionPolicy();p.id=UUID.randomUUID();p.memberId=memberId;p.update("ALERT_ONLY",false,false,false);return p;}
    public void update(String mode,boolean challenge,boolean hold,boolean block){this.mode=mode;this.challengeEnabled=challenge;this.holdEnabled=hold;this.blockEnabled=block;this.updatedAt=Instant.now();}
    public String mode(){return mode;} public boolean challengeEnabled(){return challengeEnabled;} public boolean holdEnabled(){return holdEnabled;} public boolean blockEnabled(){return blockEnabled;} public Instant updatedAt(){return updatedAt;}
}
