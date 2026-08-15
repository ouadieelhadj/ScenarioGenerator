package com.staging.sg.member.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.member.contracts.SwitchFraudFeature;
import com.staging.sg.member.contracts.SwitchFraudOverview;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SwitchFraudService {
    private final String platformBaseUrl;
    private final RestClient client;

    public SwitchFraudService(@Value("${switch.member.fraud-base-url:}") String platformBaseUrl) {
        this.platformBaseUrl = normalize(platformBaseUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public SwitchFraudOverview overview() {
        JsonNode capabilities = capabilities();
        boolean configured = !platformBaseUrl.isBlank();
        boolean reachable = capabilities != null;
        List<SwitchFraudFeature> features = List.of(
                feature(capabilities, "transactionScoring", "TRANSACTION_SCORING", "Scoring transactionnel"),
                feature(capabilities, "alertOnly", "ALERT_ONLY", "Mode alerte seule"),
                feature(capabilities, "alerts", "ALERTS_CASES", "Alertes et dossiers fraude"),
                feature(capabilities, "analystFeedback", "ANALYST_FEEDBACK", "Feedback analyste"),
                feature(capabilities, "adaptiveControls", "ADAPTIVE_CONTROLS", "Contrôles adaptatifs"),
                feature(capabilities, "threatIntelligence", "THREAT_INTELLIGENCE", "Veille fraude mutualisée"));
        String status = !configured ? "UNKNOWN" : reachable ? "UP" : "DOWN";
        return new SwitchFraudOverview("1.0", "SWITCH", "ALERT_ONLY", status, configured,
                features, Instant.now(), UUID.randomUUID().toString());
    }

    private SwitchFraudFeature feature(JsonNode capabilities, String property, String code, String label) {
        boolean available = capabilities != null && capabilities.path(property).asBoolean(false);
        return new SwitchFraudFeature(code, label, available ? "AVAILABLE" : "UNAVAILABLE", available,
                available ? null : platformBaseUrl.isBlank()
                        ? "sg-fraud-platform n'est pas configuré dans le BFF Switch."
                        : capabilities == null ? "sg-fraud-platform est inaccessible."
                        : "La capacité n'est pas annoncée par sg-fraud-platform.");
    }

    private JsonNode capabilities() {
        if (platformBaseUrl.isBlank()) return null;
        try {
            JsonNode health = client.get().uri(platformBaseUrl + "/api/fraud/v1/health")
                    .retrieve().body(JsonNode.class);
            if (health == null || !"UP".equalsIgnoreCase(health.path("status").asText())) return null;
            return client.get().uri(platformBaseUrl + "/api/fraud/v1/capabilities")
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    public ResponseEntity<byte[]> forward(String path, String query, HttpMethod method, HttpHeaders incoming, byte[] body) {
        if (platformBaseUrl.isBlank()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Fraud platform is not configured".getBytes());
        String suffix = query == null || query.isBlank() ? "" : "?" + query;
        try {
            RestClient.RequestBodySpec request = client.method(method).uri(URI.create(platformBaseUrl + path + suffix))
                    .headers(h -> copy(incoming, h, HttpHeaders.AUTHORIZATION))
                    .headers(h -> copy(incoming, h, HttpHeaders.ACCEPT))
                    .headers(h -> copy(incoming, h, HttpHeaders.CONTENT_TYPE));
            if (body != null && body.length > 0) request.body(body);
            return request.retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException response) {
            return ResponseEntity.status(response.getStatusCode()).body(response.getResponseBodyAsByteArray());
        }
    }

    private void copy(HttpHeaders source, HttpHeaders target, String name) {
        if (source.containsKey(name)) target.put(name, source.get(name));
    }
    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }
}
