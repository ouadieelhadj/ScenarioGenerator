package com.staging.sg.fraud.domain;
import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="fraud_monitoring_subject",uniqueConstraints=@UniqueConstraint(name="uk_fraud_subject_member_sector_type_hash",columnNames={"member_id","sector_id","subject_type","subject_hash"}))
public class FraudMonitoringSubject{
 @Id private UUID id;@Column(name="member_id",nullable=false,length=64,updatable=false)private String memberId;@Column(name="sector_id",nullable=false,length=64,updatable=false)private String sectorId;@Column(name="subject_type",nullable=false,length=32,updatable=false)private String subjectType;@Column(name="subject_hash",nullable=false,length=64,updatable=false)private String subjectHash;@Column(nullable=false,length=24)private String status;@Column(name="created_at",nullable=false,updatable=false)private Instant createdAt;protected FraudMonitoringSubject(){}
 public static FraudMonitoringSubject enroll(String memberId,String sectorId,String subjectType,String subjectHash){FraudMonitoringSubject s=new FraudMonitoringSubject();s.id=UUID.randomUUID();s.memberId=memberId;s.sectorId=sectorId;s.subjectType=subjectType;s.subjectHash=subjectHash;s.status="MONITORED";s.createdAt=Instant.now();return s;}
 public UUID id(){return id;}public String status(){return status;}public Instant createdAt(){return createdAt;}
}
