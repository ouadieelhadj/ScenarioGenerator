package com.staging.sg.fraud.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public FraudShield contract. Internal persistence and scores remain isolated from this DTO. */
public final class CommercialRiskApi {
    private CommercialRiskApi() {}

    public record RiskScoreRequest(
            @JsonProperty("transaction_id") @NotBlank @Size(max=128) String transactionId,
            @JsonProperty("customer_id") @Size(max=128) String customerId,
            @JsonProperty("account_id") @Size(max=128) String accountId,
            @JsonProperty("instrument_id") @Size(max=128) String instrumentId,
            @JsonProperty("card_token") @Size(max=128) String cardToken,
            @NotBlank @Size(max=32) String channel,
            @NotBlank @Size(max=32) String type,
            @PositiveOrZero long amount,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
            @JsonProperty("merchant_id") @Size(max=128) String merchantId,
            @JsonProperty("device_id") @Size(max=128) String deviceId,
            @Size(max=128) String ip,
            @NotBlank @Pattern(regexp="[A-Z]{2,3}") String country,
            Instant timestamp,
            @Pattern(regexp="[0-9]{4}") String mcc,
            @JsonProperty("beneficiary_id") @Size(max=128) String beneficiaryId,
            @JsonProperty("card_present") boolean cardPresent,
            @JsonProperty("strong_authentication") boolean strongAuthentication,
            @JsonProperty("attempts_last_hour") @Min(0) @Max(10000) int attemptsLastHour,
            Map<@Size(max=64) String, Boolean> signals) {
        public RiskScoreRequest {
            signals = signals == null ? Map.of() : Map.copyOf(signals);
        }
        public String protectedInstrumentReference() {
            String value = cardToken != null && !cardToken.isBlank() ? cardToken : instrumentId;
            if (value == null || value.isBlank()) throw new IllegalArgumentException("card_token or instrument_id is required");
            return value;
        }
    }

    public record RiskScoreResponse(
            @JsonProperty("assessment_id") UUID assessmentId,
            @JsonProperty("transaction_id") String transactionId,
            @JsonProperty("risk_score") int riskScore,
            String decision,
            @JsonProperty("enforced_action") String enforcedAction,
            @JsonProperty("risk_level") String riskLevel,
            @JsonProperty("fraud_type") String fraudType,
            double confidence,
            List<String> reasons,
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("assessed_at") Instant assessedAt) {}
}
