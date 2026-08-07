package com.staging.sg.member.bff.service;

import com.staging.sg.member.contracts.SwitchInterfaceCapability;
import com.staging.sg.member.contracts.SwitchInterfaceDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchInterfaceService {
 private final String baseUrl;private final RestClient client=RestClient.create();
 public SwitchInterfaceService(@Value("${switch.backend.base-url:}")String baseUrl){this.baseUrl=baseUrl==null?"":baseUrl.replaceAll("/+$","");}
 public SwitchInterfaceCapability capability(String authorization){if(baseUrl.isBlank())return blocked("Switch member backend is not configured");try{return client.get().uri(baseUrl+"/api/member/v1/interfaces/capabilities").header(HttpHeaders.AUTHORIZATION,authorization).retrieve().body(SwitchInterfaceCapability.class);}catch(RuntimeException absent){return blocked("Interface registry and Maker/Checker APIs are absent");}}
 public SwitchInterfaceDefinition[] interfaces(String authorization){SwitchInterfaceCapability capability=capability(authorization);if(!capability.registryAvailable())return new SwitchInterfaceDefinition[0];SwitchInterfaceDefinition[] result=client.get().uri(baseUrl+"/api/member/v1/interfaces").header(HttpHeaders.AUTHORIZATION,authorization).retrieve().body(SwitchInterfaceDefinition[].class);return result==null?new SwitchInterfaceDefinition[0]:result;}
 private SwitchInterfaceCapability blocked(String reason){return new SwitchInterfaceCapability(false,false,false,reason);}
}
