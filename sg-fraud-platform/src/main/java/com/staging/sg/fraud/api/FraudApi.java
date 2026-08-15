package com.staging.sg.fraud.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FraudApi {
    private FraudApi() {}
    public record EnrollmentRequest(@NotBlank @Size(max=128) String tokenReference,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String country,
            @Size(max=64) String customerReference) {}
    public record EnrollmentResponse(UUID enrollmentId, String status, Instant createdAt) {}
    public record ScoreRequest(@NotBlank @Size(max=128) String transactionReference,
            @NotBlank @Size(max=128) String tokenReference,
            @PositiveOrZero long amountMinor,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String country,
            @NotBlank @Pattern(regexp="[0-9]{4}") String mcc,
            @NotBlank @Size(max=32) String channel,
            boolean cardPresent, boolean strongAuthentication,
            @Min(0) @Max(20) int attemptsLastHour,
            @Size(max=64) String deviceReference) {}
    public record RiskReason(String code, int contribution, String explanation) {}
    public record ScoreResponse(UUID assessmentId, int score, String band,
            String recommendedAction, String enforcedAction, String modelVersion,
            List<RiskReason> reasons, UUID alertId, Instant assessedAt) {}
    public record AlertView(UUID id, String transactionReference, int score, String band,
            String status, Instant createdAt) {}
    public record FeedbackRequest(@NotBlank @Pattern(regexp="CONFIRMED_FRAUD|LEGITIMATE|INCONCLUSIVE") String outcome,
            @Size(max=500) String comment) {}
    public record FeedbackResponse(UUID feedbackId, UUID alertId, String outcome, Instant createdAt) {}
    public record CaseRequest(@NotNull UUID alertId, @NotBlank @Size(max=160) String title) {}
    public record CaseView(UUID id, UUID alertId, String title, String status, Instant createdAt) {}
    public record ControlBacktestRequest(@NotBlank @Size(max=120) String name,
            @Positive int labeledTransactions, @PositiveOrZero int truePositives,
            @PositiveOrZero int falsePositives, @PositiveOrZero int falseNegatives) {}
    public record ControlBacktestResponse(UUID id, String status, double precision,
            double recall, double falsePositiveRate, String governanceDecision, Instant createdAt) {}
    public record BatchScoreRequest(@NotEmpty @Size(max=1000) List<@Valid ScoreRequest> transactions) {}
    public record BatchScoreResponse(int received, int assessed, int alerts, String mode,
            List<ScoreResponse> results) {}
    public record ThreatSignalRequest(@NotBlank @Size(max=64) String indicatorType,
            @NotBlank @Size(max=64) String indicatorHash, @Min(1) @Max(100) int severity,
            @NotBlank @Size(max=64) String source, Instant expiresAt) {}
    public record ThreatSignalResponse(UUID id, String status, Instant createdAt) {}
    public record Health(String status, String mode) {}
    public record Capabilities(String mode, boolean cardMonitoringEnrollment,
            boolean transactionScoring, boolean explainableScoring, boolean alertOnly,
            boolean alerts, boolean analystFeedback, boolean adaptiveControls,
            boolean threatIntelligence, boolean syntheticInjection, boolean batchScoring,
            boolean fraudMetrics, boolean evidenceExport, boolean transactionBlocking) {}
}
