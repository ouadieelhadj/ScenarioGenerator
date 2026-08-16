package com.staging.sg.fraud.gateway.api;
import jakarta.validation.constraints.*;import java.util.*;
public final class IsoFraudApi{private IsoFraudApi(){}
 public record IsoMessageRequest(@NotBlank String mti,@NotEmpty Map<@Pattern(regexp="[0-9]{1,3}")String,@NotBlank String> fields){}
 public record IsoMessageResponse(String mti,Map<String,String> fields,int fraudScore,String fraudAction,String enforcedAction){}
 public record CanonicalEventRequest(@NotBlank @Size(max=128)String transactionReference,@NotBlank @Size(max=128)String tokenReference,
  @PositiveOrZero long amountMinor,@NotBlank @Pattern(regexp="[A-Z]{3}")String currency,@NotBlank @Pattern(regexp="[A-Z]{3}")String country,
  @NotBlank @Pattern(regexp="[0-9]{4}")String mcc,@NotBlank @Size(max=32)String channel,boolean cardPresent,boolean strongAuthentication,
  @Min(0)@Max(20)int attemptsLastHour,@Size(max=128)String deviceReference,@Size(max=128)String customerReference,
  @Size(max=128)String accountReference,@Size(max=128)String beneficiaryReference,@Size(max=128)String merchantReference,
  @Size(max=128)String ipReference,@Size(max=64)String sector){}
 public record GatewayDecisionResponse(String transactionReference,int score,String recommendedAction,String enforcedAction,
  String band,String responseChannel,String correlationReference){}
 public record LabScenarioRequest(@NotBlank @Pattern(regexp="ATM_WITHDRAWAL|POS_PURCHASE|ECOMMERCE_PURCHASE|MOBILE_TRANSFER|COORDINATED_GROUP")String scenario,
  @Min(1)@Max(1000)int transactionCount){}
 public record LabScenarioResponse(String scenario,int injected,int alerts,int challenged,int held,int blocked,List<GatewayDecisionResponse> sample){}
 public record Health(String status,String mode){}
}
