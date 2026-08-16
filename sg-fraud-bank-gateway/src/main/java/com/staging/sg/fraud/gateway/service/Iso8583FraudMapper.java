package com.staging.sg.fraud.gateway.service;
import com.fasterxml.jackson.databind.JsonNode;import com.staging.sg.fraud.gateway.api.IsoFraudApi.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;import java.util.*;
@Component public class Iso8583FraudMapper{
 private final String tokenField,scoreField,scaField;
 @Value("${fraud-gateway.iso.rc-allow:00}")private String allowRc="00";@Value("${fraud-gateway.iso.rc-alert:00}")private String alertRc="00";
 @Value("${fraud-gateway.iso.rc-challenge:00}")private String challengeRc="00";@Value("${fraud-gateway.iso.rc-hold:00}")private String holdRc="00";@Value("${fraud-gateway.iso.rc-block:00}")private String blockRc="00";
 public Iso8583FraudMapper(@Value("${fraud-gateway.iso.token-field:62}")int token,@Value("${fraud-gateway.iso.score-response-field:63}")int score,@Value("${fraud-gateway.iso.sca-marker-field:48}")int sca){tokenField=String.valueOf(token);scoreField=String.valueOf(score);scaField=String.valueOf(sca);}
 public Map<String,Object> toCanonical(IsoMessageRequest request){
  Map<String,String> f=request.fields(); if(f.containsKey("2"))throw new IllegalArgumentException("DE2/PAN is forbidden; provide a network token reference in DE"+tokenField);
  String token=required(f,tokenField);String currency=currency(required(f,"49"));String country=country(f.getOrDefault("19","504"));String entry=f.getOrDefault("22","");
  Map<String,Object> out=new LinkedHashMap<>();out.put("transactionReference",required(f,"37"));out.put("tokenReference",token);out.put("amountMinor",Long.parseLong(required(f,"4")));out.put("currency",currency);out.put("country",country);out.put("mcc",required(f,"18"));out.put("channel",entry.startsWith("01")||entry.startsWith("81")?"ECOMMERCE":"POS");out.put("cardPresent",!(entry.startsWith("01")||entry.startsWith("81")));out.put("strongAuthentication",f.getOrDefault(scaField,"").contains("SCA=Y"));out.put("attemptsLastHour",0);if(f.containsKey("41"))out.put("deviceReference",f.get("41"));if(f.containsKey("42"))out.put("merchantReference",f.get("42"));return Map.copyOf(out);
 }
 public IsoMessageResponse response(IsoMessageRequest request,JsonNode score){String enforced=score.path("enforcedAction").asText("ALERT");Map<String,String> fields=new LinkedHashMap<>(request.fields());fields.put("39",responseCode(enforced));fields.put(scoreField,"FRAUD_SCORE="+score.path("score").asInt()+";RECOMMENDED="+score.path("recommendedAction").asText()+";ENFORCED="+enforced);return new IsoMessageResponse(responseMti(request.mti()),Map.copyOf(fields),score.path("score").asInt(),score.path("recommendedAction").asText(),enforced);}
 private String required(Map<String,String>f,String key){String v=f.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing DE"+key);return v;}
 private String currency(String n){return switch(n){case"504"->"MAD";case"978"->"EUR";case"840"->"USD";default->throw new IllegalArgumentException("Unsupported ISO currency: "+n);};}
 private String country(String n){return switch(n){case"504"->"MAR";case"250"->"FRA";case"840"->"USA";default->throw new IllegalArgumentException("Unsupported ISO country: "+n);};}
 private String responseMti(String mti){if(!mti.matches("[0-9]{4}"))throw new IllegalArgumentException("Invalid MTI");char[]c=mti.toCharArray();c[2]=(char)(c[2]+1);return new String(c);}
 private String responseCode(String action){return switch(action){case"ALLOW"->allowRc;case"CHALLENGE"->challengeRc;case"HOLD"->holdRc;case"BLOCK"->blockRc;default->alertRc;};}
}
