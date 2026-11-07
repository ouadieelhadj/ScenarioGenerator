package com.staging.sg.fraud.portal.service;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

@Service
public class FraudPortalUpstreamClient {
    private static final Logger LOG = LoggerFactory.getLogger(FraudPortalUpstreamClient.class);
    private static final Set<String> SAFE_HEADERS = Set.of(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE,
            "Idempotency-Key", "X-Correlation-ID", "X-Fraud-Sector");
    private final URI apiRoot;
    private final RestClient client;

    public FraudPortalUpstreamClient(FraudPortalProperties properties) {
        this.apiRoot = properties.apiRoot();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutMs());
        factory.setReadTimeout(properties.readTimeoutMs());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public ResponseEntity<byte[]> forward(String relativePath, String query, HttpMethod method,
            HttpHeaders incoming, byte[] body, String accessToken) {
        URI target = target(relativePath, query);
        try {
            RestClient.RequestBodySpec request = client.method(method).uri(target)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        SAFE_HEADERS.forEach(name -> {
                            if (incoming.containsKey(name)) headers.put(name, incoming.get(name));
                        });
                    });
            if (body != null && body.length > 0) request.body(body);
            return request.exchange((outgoing, response) -> {
                HttpHeaders responseHeaders = new HttpHeaders();
                response.getHeaders().forEach((name, values) -> {
                    if (name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) responseHeaders.put(name, values);
                });
                byte[] payload;
                try {
                    payload = response.getBody().readAllBytes();
                } catch (java.io.IOException emptyErrorBody) {
                    payload = new byte[0];
                }
                return new ResponseEntity<>(payload, responseHeaders, response.getStatusCode());
            });
        } catch (RestClientResponseException response) {
            HttpHeaders headers = new HttpHeaders();
            if (response.getResponseHeaders() != null) {
                response.getResponseHeaders().forEach((name, values) -> {
                    if (name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) headers.put(name, values);
                });
            }
            return new ResponseEntity<>(response.getResponseBodyAsByteArray(), headers, response.getStatusCode());
        } catch (RuntimeException unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            LOG.warn("Fraud upstream request failed: target={}, exception={}, cause={}", target,
                    unavailable.getClass().getSimpleName(), cause.getClass().getSimpleName());
            System.err.printf("FRAUD_UPSTREAM_FAILED|target=%s|exception=%s|cause=%s%n", target,
                    unavailable.getClass().getSimpleName(), cause.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Fraud upstream unavailable".getBytes());
        }
    }

    URI target(String relativePath, String query) {
        String clean = relativePath == null ? "" : relativePath.replaceFirst("^/+", "");
        if (clean.contains("..") || clean.contains("\\\\") || clean.contains("://")) {
            throw new IllegalArgumentException("Invalid upstream path");
        }
        // Force a relative path: RFC URI resolution otherwise treats an action
        // such as batch-jobs:prevalidate as a scheme instead of an API segment.
        URI target = apiRoot.resolve("./" + clean + (query == null || query.isBlank() ? "" : "?" + query));
        String root = apiRoot.normalize().toString();
        if (!target.normalize().toString().startsWith(root)) throw new IllegalArgumentException("Invalid upstream target");
        return target;
    }
}
