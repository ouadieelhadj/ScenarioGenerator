package com.staging.sg.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="fraud_ai_policy",uniqueConstraints=@UniqueConstraint(name="uk_fraud_ai_policy_member_sector",columnNames={"member_id","sector_id"}))
public class FraudAiPolicy {
    @Id private UUID id;
    @Column(name="member_id",nullable=false,length=64,updatable=false)private String memberId;
    @Column(name="sector_id",nullable=false,length=64,updatable=false)private String sectorId;
    @Column(nullable=false)private boolean enabled;
    @Column(name="governance_mode",nullable=false,length=16)private String governanceMode;
    @Column(name="champion_model",nullable=false,length=96)private String championModel;
    @Column(name="challenger_model",length=96)private String challengerModel;
    @Column(name="challenger_traffic_percent",nullable=false)private int challengerTrafficPercent;
    @Column(name="minimum_precision",nullable=false)private double minimumPrecision;
    @Column(name="minimum_recall",nullable=false)private double minimumRecall;
    @Column(name="maximum_false_positive_rate",nullable=false)private double maximumFalsePositiveRate;
    @Column(name="drift_threshold",nullable=false)private double driftThreshold;
    @Column(name="drift_status",nullable=false,length=16)private String driftStatus;
    @Column(name="explainability_required",nullable=false)private boolean explainabilityRequired;
    @Column(name="analyst_approval_required",nullable=false)private boolean analystApprovalRequired;
    @Column(name="alert_threshold",nullable=false)private int alertThreshold;
    @Column(name="challenge_threshold",nullable=false)private int challengeThreshold;
    @Column(name="hold_threshold",nullable=false)private int holdThreshold;
    @Column(name="block_threshold",nullable=false)private int blockThreshold;
    @Column(name="updated_at",nullable=false)private Instant updatedAt;
    @Version private long version;
    protected FraudAiPolicy(){}
    public static FraudAiPolicy defaults(String memberId,String sectorId,String defaultModel){FraudAiPolicy p=new FraudAiPolicy();p.id=UUID.randomUUID();p.memberId=memberId;p.sectorId=sectorId;p.update(false,"SHADOW",defaultModel,null,0,.80,.70,.05,.20,"HEALTHY",true,true,350,650,800,900);return p;}
    public void update(boolean enabled,String mode,String champion,String challenger,int challengerPercent,double minPrecision,double minRecall,double maxFpr,double driftThreshold,String driftStatus,boolean explainabilityRequired,boolean analystApprovalRequired,int alert,int challenge,int hold,int block){this.enabled=enabled;this.governanceMode=mode;this.championModel=champion;this.challengerModel=challenger;this.challengerTrafficPercent=challengerPercent;this.minimumPrecision=minPrecision;this.minimumRecall=minRecall;this.maximumFalsePositiveRate=maxFpr;this.driftThreshold=driftThreshold;this.driftStatus=driftStatus;this.explainabilityRequired=explainabilityRequired;this.analystApprovalRequired=analystApprovalRequired;this.alertThreshold=alert;this.challengeThreshold=challenge;this.holdThreshold=hold;this.blockThreshold=block;this.updatedAt=Instant.now();}
    public UUID id(){return id;}public String memberId(){return memberId;}public String sectorId(){return sectorId;}public boolean enabled(){return enabled;}public String governanceMode(){return governanceMode;}public String championModel(){return championModel;}public String challengerModel(){return challengerModel;}public int challengerTrafficPercent(){return challengerTrafficPercent;}public double minimumPrecision(){return minimumPrecision;}public double minimumRecall(){return minimumRecall;}public double maximumFalsePositiveRate(){return maximumFalsePositiveRate;}public double driftThreshold(){return driftThreshold;}public String driftStatus(){return driftStatus;}public boolean explainabilityRequired(){return explainabilityRequired;}public boolean analystApprovalRequired(){return analystApprovalRequired;}public int alertThreshold(){return alertThreshold;}public int challengeThreshold(){return challengeThreshold;}public int holdThreshold(){return holdThreshold;}public int blockThreshold(){return blockThreshold;}public Instant updatedAt(){return updatedAt;}
}
