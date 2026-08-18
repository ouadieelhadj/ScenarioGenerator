package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="fraud_graph_policy",uniqueConstraints=@UniqueConstraint(name="uk_fraud_graph_policy_member_sector",columnNames={"member_id","sector_id"}))
public class FraudGraphPolicy {
    public static final String DEFAULT_TYPES="DEVICE,CUSTOMER,ACCOUNT,BENEFICIARY,MERCHANT,IP";
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false) private String memberId;
    @Column(name="sector_id",nullable=false,length=64,updatable=false) private String sectorId;
    @Column(nullable=false) private boolean enabled;
    @Column(name="cross_sector_enabled",nullable=false) private boolean crossSectorEnabled;
    @Column(name="allowed_entity_types",nullable=false,length=256) private String allowedEntityTypes;
    @Column(name="minimum_group_size",nullable=false) private int minimumGroupSize;
    @Column(name="base_score",nullable=false) private int baseScore;
    @Column(name="score_per_additional_subject",nullable=false) private int scorePerAdditionalSubject;
    @Column(name="maximum_score",nullable=false) private int maximumScore;
    @Column(name="observation_window_minutes",nullable=false) private int observationWindowMinutes;
    @Column(name="minimum_observations",nullable=false) private int minimumObservations;
    @Column(name="maximum_hops",nullable=false) private int maximumHops;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version private long version;
    protected FraudGraphPolicy(){}
    public static FraudGraphPolicy defaults(String memberId,String sectorId){FraudGraphPolicy p=new FraudGraphPolicy();p.id=UUID.randomUUID();p.memberId=memberId;p.sectorId=sectorId;p.update(true,true,DEFAULT_TYPES,3,180,12,700,10080,1,2);return p;}
    public void update(boolean enabled,boolean crossSector,String types,int minimumGroupSize,int baseScore,int increment,int maximumScore,int windowMinutes,int minimumObservations,int maximumHops){this.enabled=enabled;this.crossSectorEnabled=crossSector;this.allowedEntityTypes=normalize(types);this.minimumGroupSize=minimumGroupSize;this.baseScore=baseScore;this.scorePerAdditionalSubject=increment;this.maximumScore=maximumScore;this.observationWindowMinutes=windowMinutes;this.minimumObservations=minimumObservations;this.maximumHops=maximumHops;this.updatedAt=Instant.now();}
    private String normalize(String value){return String.join(",",Arrays.stream(value.split(",")).map(String::trim).map(String::toUpperCase).filter(v->v.matches("[A-Z_]{2,32}")).distinct().sorted().toList());}
    public UUID id(){return id;}public String memberId(){return memberId;}public String sectorId(){return sectorId;}public boolean enabled(){return enabled;}public boolean crossSectorEnabled(){return crossSectorEnabled;}public String allowedEntityTypes(){return allowedEntityTypes;}public Set<String> allowedTypes(){return Set.of(allowedEntityTypes.split(","));}public int minimumGroupSize(){return minimumGroupSize;}public int baseScore(){return baseScore;}public int scorePerAdditionalSubject(){return scorePerAdditionalSubject;}public int maximumScore(){return maximumScore;}public int observationWindowMinutes(){return observationWindowMinutes;}public int minimumObservations(){return minimumObservations;}public int maximumHops(){return maximumHops;}public Instant updatedAt(){return updatedAt;}
}
