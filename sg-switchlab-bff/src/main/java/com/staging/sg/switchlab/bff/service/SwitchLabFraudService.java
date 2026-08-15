package com.staging.sg.switchlab.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.staging.sg.switchlab.contracts.SwitchLabFraudFeature;
import com.staging.sg.switchlab.contracts.SwitchLabFraudOverview;
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
public class SwitchLabFraudService {
    private final String platformBaseUrl;
    private final RestClient client;

    public SwitchLabFraudService(@Value("${switchlab.fraud.base-url:}") String platformBaseUrl) {
        this.platformBaseUrl = normalize(platformBaseUrl);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public SwitchLabFraudOverview overview() {
        JsonNode capabilities = capabilities();
        boolean configured = !platformBaseUrl.isBlank();
        boolean reachable = capabilities != null;
        List<SwitchLabFraudFeature> features = List.of(
                feature(capabilities, "syntheticInjection", "SYNTHETIC_INJECTION", "Injection synthétique"),
                feature(capabilities, "batchScoring", "BATCH_SCORING", "Scoring batch"),
                feature(capabilities, "alertOnly", "ALERT_ONLY", "Observation sans blocage"),
                feature(capabilities, "adaptiveControls", "ADAPTIVE_BACKTEST", "Backtest des contrôles candidats"),
                feature(capabilities, "fraudMetrics", "FRAUD_METRICS", "Précision, rappel et faux positifs"),
                feature(capabilities, "evidenceExport", "EVIDENCE_EXPORT", "Dossier de preuves POC"));
        String status = !configured ? "UNKNOWN" : reachable ? "UP" : "DOWN";
        return new SwitchLabFraudOverview("1.0", "SWITCHLAB", "LAB_ALERT_ONLY", status, configured,
                features, Instant.now(), UUID.randomUUID().toString());
    }

    private SwitchLabFraudFeature feature(JsonNode capabilities, String property, String code, String label) {
        boolean available = capabilities != null && capabilities.path(property).asBoolean(false);
        return new SwitchLabFraudFeature(code, label, available ? "AVAILABLE" : "UNAVAILABLE", available,
                available ? null : platformBaseUrl.isBlank()
                        ? "sg-fraud-platform n'est pas configuré dans le BFF SwitchLab."
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
