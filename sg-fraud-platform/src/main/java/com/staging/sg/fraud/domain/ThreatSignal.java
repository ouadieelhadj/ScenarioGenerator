package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="fraud_threat_signal",uniqueConstraints=@UniqueConstraint(name="uk_fraud_signal_member_hash",columnNames={"member_id","indicator_type","indicator_hash"}))
public class ThreatSignal {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64) private String memberId;
    @Column(name="indicator_type",nullable=false,length=64) private String indicatorType;
    @Column(name="indicator_hash",nullable=false,length=64) private String indicatorHash;
    @Column(nullable=false) private int severity;
    @Column(nullable=false,length=64) private String source;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected ThreatSignal() {}
    public static ThreatSignal create(String m,String t,String h,int s,String source,Instant expires){ThreatSignal x=new ThreatSignal();x.id=UUID.randomUUID();x.memberId=m;x.indicatorType=t;x.indicatorHash=h;x.severity=s;x.source=source;x.expiresAt=expires;x.createdAt=Instant.now();return x;}
    public UUID id(){return id;} public Instant createdAt(){return createdAt;}
}
