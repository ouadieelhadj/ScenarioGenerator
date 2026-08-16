package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="fraud_entity_link", uniqueConstraints=@UniqueConstraint(name="uk_fraud_link_member_subject_entity", columnNames={"member_id","subject_hash","entity_type","entity_hash"}))
public class FraudEntityLink {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="subject_hash",nullable=false,length=64,updatable=false) private String subjectHash;
    @Column(name="entity_type",nullable=false,length=32,updatable=false) private String entityType;
    @Column(name="entity_hash",nullable=false,length=64,updatable=false) private String entityHash;
    @Column(name="observation_count",nullable=false) private long observationCount;
    @Column(name="first_seen_at",nullable=false,updatable=false) private Instant firstSeenAt;
    @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
    protected FraudEntityLink() {}
    public static FraudEntityLink first(String memberId,String subjectHash,String type,String entityHash){
        FraudEntityLink value=new FraudEntityLink(); value.id=UUID.randomUUID(); value.memberId=memberId;
        value.subjectHash=subjectHash; value.entityType=type; value.entityHash=entityHash;
        value.observationCount=1; value.firstSeenAt=Instant.now(); value.lastSeenAt=value.firstSeenAt; return value;
    }
    public void observe(){observationCount++;lastSeenAt=Instant.now();}
    public UUID id(){return id;} public long observationCount(){return observationCount;}
}
