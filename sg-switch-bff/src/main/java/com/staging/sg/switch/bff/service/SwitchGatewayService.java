package com.staging.sg.member.bff.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SwitchGatewayService {
 private final String baseUrl;private final RestClient client=RestClient.create();
 public SwitchGatewayService(@Value("${switch.backend.base-url:}")String baseUrl){this.baseUrl=baseUrl==null?"":baseUrl.replaceAll("/+$","");}
 public boolean authorized(String authorization){if(authorization==null||authorization.isBlank()||baseUrl.isBlank())return false;try{return client.get().uri(baseUrl+"/api/me/navigation").header(HttpHeaders.AUTHORIZATION,authorization).retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();}catch(RuntimeException rejected){return false;}}
 public ResponseEntity<byte[]> forward(String path,String query,HttpMethod method,HttpHeaders incoming,byte[] body){if(baseUrl.isBlank())return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Switch member backend is not configured".getBytes());String suffix=query==null||query.isBlank()?"":"?"+query;try{RestClient.RequestBodySpec request=client.method(method).uri(URI.create(baseUrl+path+suffix)).headers(h->copy(incoming,h,HttpHeaders.AUTHORIZATION)).headers(h->copy(incoming,h,HttpHeaders.ACCEPT)).headers(h->copy(incoming,h,HttpHeaders.CONTENT_TYPE));if(body!=null&&body.length>0)request.body(body);return request.retrieve().toEntity(byte[].class);}catch(RestClientResponseException response){return ResponseEntity.status(response.getStatusCode()).body(response.getResponseBodyAsByteArray());}}
 private void copy(HttpHeaders source,HttpHeaders target,String name){if(source.containsKey(name))target.put(name,source.get(name));}
}
