package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="fraud_feature_snapshot", uniqueConstraints=@UniqueConstraint(name="uk_fraud_feature_member_tx", columnNames={"member_id","transaction_reference"}))
public class FraudFeatureSnapshot {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="transaction_reference",nullable=false,length=128,updatable=false) private String transactionReference;
    @Column(name="feature_version",nullable=false,length=64,updatable=false) private String featureVersion;
    @Column(name="features_json",nullable=false,length=4000,updatable=false) private String featuresJson;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected FraudFeatureSnapshot() {}
    public static FraudFeatureSnapshot create(String memberId,String transactionReference,String version,String json){
        FraudFeatureSnapshot value=new FraudFeatureSnapshot(); value.id=UUID.randomUUID(); value.memberId=memberId;
        value.transactionReference=transactionReference; value.featureVersion=version; value.featuresJson=json;
        value.createdAt=Instant.now(); return value;
    }
}
