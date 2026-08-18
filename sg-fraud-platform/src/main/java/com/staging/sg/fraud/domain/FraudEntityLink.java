package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="fraud_entity_link", uniqueConstraints=@UniqueConstraint(name="uk_fraud_link_member_sector_subject_entity", columnNames={"member_id","sector_id","subject_type","subject_hash","entity_type","entity_hash"}))
public class FraudEntityLink {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="sector_id",nullable=false,length=64,updatable=false) private String sectorId;
    @Column(name="subject_type",nullable=false,length=32,updatable=false) private String subjectType;
    @Column(name="subject_hash",nullable=false,length=64,updatable=false) private String subjectHash;
    @Column(nullable=false,length=32,updatable=false) private String channel;
    @Column(name="entity_type",nullable=false,length=32,updatable=false) private String entityType;
    @Column(name="entity_hash",nullable=false,length=64,updatable=false) private String entityHash;
    @Column(name="observation_count",nullable=false) private long observationCount;
    @Column(name="first_seen_at",nullable=false,updatable=false) private Instant firstSeenAt;
    @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
    protected FraudEntityLink() {}
    public static FraudEntityLink first(String memberId,String sectorId,String subjectType,String subjectHash,String channel,String type,String entityHash){
        FraudEntityLink value=new FraudEntityLink(); value.id=UUID.randomUUID(); value.memberId=memberId;
        value.sectorId=sectorId;value.subjectType=subjectType;value.subjectHash=subjectHash;value.channel=channel;
        value.entityType=type; value.entityHash=entityHash;
        value.observationCount=1; value.firstSeenAt=Instant.now(); value.lastSeenAt=value.firstSeenAt; return value;
    }
    public void observe(){observationCount++;lastSeenAt=Instant.now();}
    public UUID id(){return id;} public String entityType(){return entityType;} public String entityHash(){return entityHash;}
    public String memberId(){return memberId;} public String sectorId(){return sectorId;} public String subjectType(){return subjectType;}
    public String subjectHash(){return subjectHash;} public String channel(){return channel;} public long observationCount(){return observationCount;}
}
