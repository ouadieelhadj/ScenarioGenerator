package com.staging.sg.fraud.api;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class FraudGovernanceApi {
    private FraudGovernanceApi(){}
    public record GraphPolicyRequest(boolean enabled,boolean crossSectorEnabled,
            @NotBlank @Size(max=256)String allowedEntityTypes,@Min(2)@Max(10000)int minimumGroupSize,
            @Min(0)@Max(1000)int baseScore,@Min(0)@Max(1000)int scorePerAdditionalSubject,
            @Min(0)@Max(1000)int maximumScore,@Min(1)@Max(525600)int observationWindowMinutes,
            @Min(1)@Max(1000)int minimumObservations,@Min(1)@Max(5)int maximumHops){}
    public record GraphPolicyResponse(String memberId,String sectorId,boolean enabled,boolean crossSectorEnabled,
            String allowedEntityTypes,int minimumGroupSize,int baseScore,int scorePerAdditionalSubject,int maximumScore,
            int observationWindowMinutes,int minimumObservations,int maximumHops,Instant updatedAt){}
    public record AiPolicyRequest(boolean enabled,@NotBlank@Pattern(regexp="SHADOW|ACTIVE")String governanceMode,
            @NotBlank@Pattern(regexp="[A-Za-z0-9._:/-]{1,96}")String championModel,@Pattern(regexp="[A-Za-z0-9._:/-]{1,96}")String challengerModel,
            @Min(0)@Max(100)int challengerTrafficPercent,@DecimalMin("0.0")@DecimalMax("1.0")double minimumPrecision,
            @DecimalMin("0.0")@DecimalMax("1.0")double minimumRecall,@DecimalMin("0.0")@DecimalMax("1.0")double maximumFalsePositiveRate,
            @DecimalMin("0.0")@DecimalMax("1.0")double driftThreshold,@NotBlank@Pattern(regexp="HEALTHY|WATCH|DRIFTED")String driftStatus,
            boolean explainabilityRequired,boolean analystApprovalRequired,@Min(0)@Max(1000)int alertThreshold,
            @Min(0)@Max(1000)int challengeThreshold,@Min(0)@Max(1000)int holdThreshold,@Min(0)@Max(1000)int blockThreshold){}
    public record AiPolicyResponse(String memberId,String sectorId,boolean enabled,String governanceMode,String championModel,
            String challengerModel,int challengerTrafficPercent,double minimumPrecision,double minimumRecall,double maximumFalsePositiveRate,
            double driftThreshold,String driftStatus,boolean explainabilityRequired,boolean analystApprovalRequired,
            int alertThreshold,int challengeThreshold,int holdThreshold,int blockThreshold,Instant updatedAt){}
}
