package com.staging.sg.fraud.gateway.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class OmnichannelApi {
    private OmnichannelApi() {}
    public static final Set<String> DOMAINS=Set.of("CARD","MOBILE_PAYMENT","INTERNET_BANKING","MOBILE_BANKING","TRANSFER","3DS");
    public static final Set<String> PROTOCOLS=Set.of("ISO8583","ISO20022","REST","KAFKA","MQ","WEBHOOK","BATCH","SDK");

    public record UniversalTransactionRequest(
            @NotBlank @Size(max=128) String transactionId,
            @NotBlank @Pattern(regexp="CARD|MOBILE_PAYMENT|INTERNET_BANKING|MOBILE_BANKING|TRANSFER|3DS") String domain,
            @NotBlank @Pattern(regexp="ISO8583|ISO20022|REST|KAFKA|MQ|WEBHOOK|BATCH|SDK") String sourceProtocol,
            @NotBlank @Size(max=32) String eventType,
            @NotBlank @Size(max=128) String instrumentToken,
            @Size(max=128) String customerToken,
            @Size(max=128) String accountToken,
            @Size(max=128) String beneficiaryToken,
            @Size(max=128) String merchantToken,
            @Size(max=128) String deviceToken,
            @Size(max=128) String ipToken,
            @PositiveOrZero long amountMinor,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String country,
            @Pattern(regexp="[0-9]{4}") String mcc,
            boolean cardPresent,
            boolean strongAuthentication,
            @Min(0) @Max(10000) int attemptsLastHour,
            Instant occurredAt,
            Map<@Size(max=64) String,Boolean> signals) {
        public UniversalTransactionRequest { signals=signals==null?Map.of():Map.copyOf(signals); occurredAt=occurredAt==null?Instant.now():occurredAt; }
    }

    public record Iso20022EvaluationRequest(
            @NotBlank @Size(max=128) String instrumentToken,
            @NotBlank @Size(max=1_000_000) String document,
            @Size(max=128) String deviceToken,
            @Size(max=128) String ipToken,
            Map<@Size(max=64)String,Boolean> signals) {
        public Iso20022EvaluationRequest { signals=signals==null?Map.of():Map.copyOf(signals); }
    }

    public record BatchEvaluationRequest(@NotEmpty @Size(max=1000) List<@Valid UniversalTransactionRequest> transactions) {}
    public record BatchEvaluationResponse(int received,int evaluated,int alerts,List<IsoFraudApi.GatewayDecisionResponse> results) {}
}
