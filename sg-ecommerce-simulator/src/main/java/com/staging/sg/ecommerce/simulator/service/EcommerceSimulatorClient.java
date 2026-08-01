package com.staging.sg.ecommerce.simulator.service;

import com.staging.sg.common.ecommerce.*;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import com.staging.sg.ecommerce.simulator.api.SimulatorPurchaseRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Service
public class EcommerceSimulatorClient {
    private final RestClient acquiring;

    public EcommerceSimulatorClient(
            @Value("${ecommerce-simulator.acquiring.base-url:http://127.0.0.1:8550}")
            String baseUrl,
            @Value("${ecommerce-simulator.acquiring.connect-timeout-ms:1000}")
            int connectTimeoutMs,
            @Value("${ecommerce-simulator.acquiring.read-timeout-ms:20000}")
            int readTimeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs)).build());
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.acquiring = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(factory).build();
    }

    public EcommercePurchaseResponse purchase(SimulatorPurchaseRequest input) {
        require(input);
        String transactionId = value(input.transactionId(), UUID.randomUUID().toString());
        String correlationId = value(input.correlationId(),
                "ecom-corr-" + transactionId);
        String idempotencyKey = value(input.idempotencyKey(),
                "ecom-idem-" + transactionId);
        EcommercePurchaseRequest request = new EcommercePurchaseRequest(
                "1.0", transactionId, correlationId, idempotencyKey,
                input.acquirerId(), input.profileId(), input.merchantOrderId(),
                input.amountMinor(), input.currency(), PaymentIdentifierType.PAN,
                input.pan(), input.expiry(), input.networkRoute(),
                EcommerceAuthenticationStatus.NOT_PERFORMED,
                null, null, null);
        EcommercePurchaseResponse response = acquiring.post()
                .uri("/api/acquiring/v1/ecommerce/transactions")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-ID", correlationId)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (ignored, result) -> {
                    throw new IllegalStateException(
                            "Acquiring rejected ecommerce purchase: "
                                    + result.getStatusCode().value());
                })
                .body(EcommercePurchaseResponse.class);
        if (response == null) throw new IllegalStateException("Empty acquiring response");
        return response;
    }

    private static void require(SimulatorPurchaseRequest input) {
        if (input == null || input.profileId() == null || blank(input.acquirerId())
                || blank(input.merchantOrderId()) || input.amountMinor() <= 0
                || input.currency() == null || !input.currency().matches("\\d{3}")
                || input.pan() == null || !input.pan().matches("\\d{12,19}")
                || input.expiry() == null || !input.expiry().matches("\\d{4}")
                || input.networkRoute() == null) {
            throw new IllegalArgumentException("Invalid ecommerce simulator purchase");
        }
        if (input.networkRoute() == EcommerceNetworkRoute.VISA) {
            throw new IllegalArgumentException("Visa simulator route is not implemented");
        }
    }

    private static String value(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
