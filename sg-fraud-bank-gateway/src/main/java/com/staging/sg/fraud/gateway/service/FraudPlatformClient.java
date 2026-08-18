package com.staging.sg.fraud.gateway.service;
import com.fasterxml.jackson.databind.JsonNode;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.HttpHeaders;import org.springframework.stereotype.Service;import org.springframework.web.client.RestClient;import java.util.Map;
@Service public class FraudPlatformClient{private final RestClient client;private final GatewayServiceCredentialProvider credentials;public FraudPlatformClient(@Value("${fraud-gateway.platform-base-url}")String base,GatewayServiceCredentialProvider credentials){client=RestClient.builder().baseUrl(base.replaceAll("/+$","")).build();this.credentials=credentials;}
 public JsonNode score(String authorization,Map<String,Object>request){return doScore(authorization,request);}
 public JsonNode enroll(String authorization,Map<String,Object>request){return client.post().uri("/api/fraud/v1/cards/monitoring-enrollments").header(HttpHeaders.AUTHORIZATION,authorization).body(request).retrieve().body(JsonNode.class);}
 public JsonNode scorePermanentLink(Map<String,Object>request){return scorePermanentLink(null,request);}
 public JsonNode scorePermanentLink(String credentialReference,Map<String,Object>request){return doScore("Bearer "+credentials.requireToken(credentialReference),request);}
 private JsonNode doScore(String authorization,Map<String,Object>request){return client.post().uri("/api/fraud/v1/risk/transactions:score").header(HttpHeaders.AUTHORIZATION,authorization).body(request).retrieve().body(JsonNode.class);}}
