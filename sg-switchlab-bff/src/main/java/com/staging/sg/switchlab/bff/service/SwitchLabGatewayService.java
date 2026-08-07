package com.staging.sg.switchlab.bff.service;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SwitchLabGatewayService {
    private final String backendBaseUrl;
    private final RestClient restClient = RestClient.create();

    public SwitchLabGatewayService(@Value("${switchlab.backend.base-url:}") String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl == null ? "" : backendBaseUrl.replaceAll("/+$", "");
    }

    public boolean authorized(String authorization) {
        if (authorization == null || authorization.isBlank() || backendBaseUrl.isBlank()) return false;
        try {
            return restClient.get().uri(backendBaseUrl + "/api/me/navigation")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    public ResponseEntity<byte[]> forward(String path, String query, HttpMethod method,
                                          HttpHeaders incoming, byte[] body) {
        if (backendBaseUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("SwitchLab backend is not configured".getBytes());
        }
        String suffix = query == null || query.isBlank() ? "" : "?" + query;
        URI target = URI.create(backendBaseUrl + path + suffix);
        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(target)
                    .headers(headers -> copyHeader(incoming, headers, HttpHeaders.AUTHORIZATION))
                    .headers(headers -> copyHeader(incoming, headers, HttpHeaders.ACCEPT))
                    .headers(headers -> copyHeader(incoming, headers, HttpHeaders.CONTENT_TYPE));
            if (body != null && body.length > 0) request.body(body);
            return request.retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException response) {
            return ResponseEntity.status(response.getStatusCode()).body(response.getResponseBodyAsByteArray());
        }
    }

    private void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
        if (source.containsKey(name)) target.put(name, source.get(name));
    }
}
