package com.staging.sg.cardnetwork.gateway.service;

import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Service
public class CardNetworkGatewayService {
    private final boolean mastercardEnabled;
    private final boolean visaEnabled;
    private final RestClient mastercard;
    private final RestClient visa;

    public CardNetworkGatewayService(
            @Value("${card-network.mastercard.enabled:false}") boolean mastercardEnabled,
            @Value("${card-network.mastercard.base-url:http://127.0.0.1:8084}") String mastercardUrl,
            @Value("${card-network.visa.enabled:false}") boolean visaEnabled,
            @Value("${card-network.visa.base-url:http://127.0.0.1:8564}") String visaUrl) {
        this.mastercardEnabled = mastercardEnabled;
        this.visaEnabled = visaEnabled;
        this.mastercard = RestClient.builder().baseUrl(mastercardUrl).build();
        this.visa = RestClient.builder().baseUrl(visaUrl).build();
    }

    public RoutingTransactionResponse route(RoutingTransactionRequest request) {
        String program = attribute(request, "cardProgram").toUpperCase(Locale.ROOT);
        return switch (program) {
            case "MASTERCARD" -> forward(request, mastercard, mastercardEnabled,
                    "Mastercard DMAS downstream is disabled");
            case "VISA" -> forward(request, visa, visaEnabled,
                    "Visa financial downstream is not available");
            default -> throw new IllegalArgumentException(
                    "Unsupported or missing cardProgram; expected VISA or MASTERCARD");
        };
    }

    private static RoutingTransactionResponse forward(
            RoutingTransactionRequest request, RestClient client,
            boolean enabled, String unavailableMessage) {
        if (!enabled) {
            throw new DownstreamUnavailableException(unavailableMessage);
        }
        RoutingTransactionResponse response = client.post()
                .uri("/api/routing/v1/transactions")
                .header("Idempotency-Key", request.idempotencyKey())
                .header("X-Correlation-ID", request.correlationId())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (ignored, result) -> {
                    throw new DownstreamUnavailableException(
                            "Card-network downstream rejected the request: "
                                    + result.getStatusCode().value());
                })
                .body(RoutingTransactionResponse.class);
        if (response == null) {
            throw new DownstreamUnavailableException("Empty card-network response");
        }
        return response;
    }

    private static String attribute(RoutingTransactionRequest request, String key) {
        if (request == null || request.attributes() == null) {
            return "";
        }
        return request.attributes().getOrDefault(key, "");
    }

    public static final class DownstreamUnavailableException extends RuntimeException {
        public DownstreamUnavailableException(String message) {
            super(message);
        }
    }
}
