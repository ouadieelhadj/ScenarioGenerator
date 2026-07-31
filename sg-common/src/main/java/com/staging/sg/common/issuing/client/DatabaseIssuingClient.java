package com.staging.sg.common.issuing.client;

import com.staging.sg.common.issuing.IssuingAuthorizationRequest;
import com.staging.sg.common.issuing.IssuingAuthorizationResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DatabaseIssuingClient {
    private final IssuingEndpointDirectory endpoints;
    private final RestClient.Builder restClient;

    public DatabaseIssuingClient(
            IssuingEndpointDirectory endpoints, RestClient.Builder restClient) {
        this.endpoints = endpoints;
        this.restClient = restClient;
    }

    public IssuingAuthorizationResponse authorize(
            String interfaceType, IssuingAuthorizationRequest request) {
        IssuingEndpoint endpoint = endpoints.requireActive(
                interfaceType, request.issuerId());
        IssuingAuthorizationRequest owned = withIssuer(
                request, endpoint.issuerId());
        SimpleClientHttpRequestFactory transport =
                new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(endpoint.connectTimeoutMs());
        transport.setReadTimeout(endpoint.readTimeoutMs());
        IssuingAuthorizationResponse response = restClient
                .requestFactory(transport)
                .baseUrl(endpoint.baseUrl())
                .build()
                .post()
                .uri("/authorizations")
                .header("Idempotency-Key", owned.idempotencyKey())
                .header("X-Correlation-ID", owned.correlationId())
                .body(owned)
                .retrieve()
                .body(IssuingAuthorizationResponse.class);
        if (response == null
                || !owned.transactionId().equals(response.transactionId())
                || !owned.correlationId().equals(response.correlationId())
                || !endpoint.issuerId().equals(response.issuerId())) {
            throw new IllegalStateException(
                    "Invalid correlated issuing response");
        }
        return response;
    }

    private static IssuingAuthorizationRequest withIssuer(
            IssuingAuthorizationRequest r, String issuerId) {
        if (r.issuerId() != null && !r.issuerId().isBlank()
                && !issuerId.equals(r.issuerId())) {
            throw new IllegalArgumentException(
                    "Request issuer does not match database endpoint");
        }
        return new IssuingAuthorizationRequest(
                r.schemaVersion(), issuerId, r.callerId(), r.transactionId(),
                r.correlationId(), r.idempotencyKey(), r.operation(),
                r.originalTransactionId(), r.paymentIdentifierType(),
                r.paymentIdentifier(), r.amountMinor(), r.currency(),
                r.localTransactionDateTime(), r.terminalId(), r.merchantId(),
                r.merchantCategoryCode(), r.countryCode(), r.cardPresent(),
                r.ecommerce(), r.pinBlockHex(), r.pinKeyDomain(),
                r.emvDataHex(), r.attributes());
    }
}
