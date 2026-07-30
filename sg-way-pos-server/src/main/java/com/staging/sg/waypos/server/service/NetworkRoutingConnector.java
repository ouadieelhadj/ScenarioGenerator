package com.staging.sg.waypos.server.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import com.staging.sg.waypos.server.config.WayPosProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class NetworkRoutingConnector {
    private final WayPosProperties properties;
    private final RestClient.Builder restClient;

    public NetworkRoutingConnector(
            WayPosProperties properties, RestClient.Builder restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public RoutingTransactionResponse send(
            String route, RoutingTransactionRequest request) {
        String baseUrl = properties.connectors().get(route);
        if (baseUrl == null || baseUrl.isBlank()) {
            return RoutingTransactionResponse.decline(request.transactionId(), "92", route);
        }
        try {
            RoutingTransactionResponse response = restClient.baseUrl(baseUrl).build()
                    .post()
                    .uri("/api/routing/v1/transactions")
                    .header("X-Correlation-ID", request.correlationId())
                    .header("Idempotency-Key", request.idempotencyKey())
                    .body(request)
                    .retrieve()
                    .body(RoutingTransactionResponse.class);
            if (response == null) {
                throw new IllegalStateException("Empty routing response");
            }
            if (!request.transactionId().equals(response.transactionId())
                    || response.status() == null
                    || response.posResponseCode() == null) {
                throw new IllegalStateException("Invalid correlated routing response");
            }
            return response;
        } catch (RuntimeException e) {
            return new RoutingTransactionResponse(
                    request.transactionId(), "UNKNOWN", "91", null, null,
                    route, null, null, true,
                    java.util.Map.of("failure", e.getClass().getSimpleName()));
        }
    }
}
