package com.staging.sg.member.bff.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class SwitchSoftPosService {
    private final String baseUrl; private final RestClient client;
    public SwitchSoftPosService(@Value("${switch.member.softpos-base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500); factory.setReadTimeout(5000);
        client = RestClient.builder().requestFactory(factory).build();
    }
    public ResponseEntity<byte[]> forward(String path, String query, HttpMethod method, HttpHeaders incoming, byte[] body) {
        if (baseUrl.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("SoftPOS backend is not configured".getBytes());
        String suffix = query == null || query.isBlank() ? "" : "?" + query;
        try {
            RestClient.RequestBodySpec request = client.method(method).uri(URI.create(baseUrl + path + suffix))
                    .headers(h -> copy(incoming, h, HttpHeaders.AUTHORIZATION))
                    .headers(h -> copy(incoming, h, HttpHeaders.ACCEPT))
                    .headers(h -> copy(incoming, h, HttpHeaders.CONTENT_TYPE));
            if (body != null && body.length > 0) request.body(body);
            return request.retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException response) {
            return ResponseEntity.status(response.getStatusCode()).body(response.getResponseBodyAsByteArray());
        }
    }
    private static void copy(HttpHeaders source, HttpHeaders target, String name) { if (source.containsKey(name)) target.put(name, source.get(name)); }
}
