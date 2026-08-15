package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_card_profile", uniqueConstraints=@UniqueConstraint(name="uk_fraud_card_member_token", columnNames={"member_id","token_hash"}))
public class CardProfile {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="token_hash",nullable=false,length=64,updatable=false) private String tokenHash;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=3) private String country;
    @Column(name="customer_reference_hash",length=64) private String customerReferenceHash;
    @Column(nullable=false,length=24) private String status;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected CardProfile() {}
    public static CardProfile enroll(String memberId,String tokenHash,String currency,String country,String customerHash) {
        CardProfile p=new CardProfile(); p.id=UUID.randomUUID(); p.memberId=memberId; p.tokenHash=tokenHash;
        p.currency=currency; p.country=country; p.customerReferenceHash=customerHash; p.status="MONITORED"; p.createdAt=Instant.now(); return p;
    }
    public UUID id(){return id;} public String memberId(){return memberId;} public String tokenHash(){return tokenHash;}
    public String status(){return status;} public Instant createdAt(){return createdAt;}
}
