package com.staging.sg.fraud.gateway.api;
import jakarta.validation.constraints.*;import java.util.Map;
public final class IsoFraudApi{private IsoFraudApi(){}
 public record IsoMessageRequest(@NotBlank String mti,@NotEmpty Map<@Pattern(regexp="[0-9]{1,3}")String,@NotBlank String> fields){}
 public record IsoMessageResponse(String mti,Map<String,String> fields,int fraudScore,String fraudAction,String enforcedAction){}
 public record Health(String status,String mode){}
}
