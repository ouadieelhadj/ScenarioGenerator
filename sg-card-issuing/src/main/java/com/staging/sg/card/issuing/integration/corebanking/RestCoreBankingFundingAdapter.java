package com.staging.sg.card.issuing.integration.corebanking;

import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import com.staging.sg.card.issuing.port.FundingAuthorizationPort;
import com.staging.sg.card.issuing.service.IssuingEndpointResolver;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class RestCoreBankingFundingAdapter implements FundingAuthorizationPort {
    private final IssuingEndpointResolver endpoints;
    private final RestClient.Builder clients;

    public RestCoreBankingFundingAdapter(
            IssuingEndpointResolver endpoints, RestClient.Builder clients) {
        this.endpoints = endpoints;
        this.clients = clients;
    }

    @Override
    public FundingResult authorize(FundingCommand command) {
        try {
            IssuingInterfaceEndpoint endpoint = endpoints.requireActive(
                    command.issuerId(), IssuingInterfaceType.CORE_BANKING);
            requireRest(endpoint);
            SimpleClientHttpRequestFactory transport =
                    new SimpleClientHttpRequestFactory();
            transport.setConnectTimeout(endpoint.connectTimeoutMs());
            transport.setReadTimeout(endpoint.readTimeoutMs());
            CoreBankingAuthorizationResponse response = clients
                    .requestFactory(transport)
                    .baseUrl(baseUrl(endpoint))
                    .build()
                    .post()
                    .uri("/authorizations")
                    .header("Idempotency-Key", command.idempotencyKey())
                    .header("X-Correlation-ID", command.correlationId())
                    .body(toRequest(command))
                    .retrieve()
                    .body(CoreBankingAuthorizationResponse.class);
            validateResponse(command, response);
            return toResult(response);
        } catch (RuntimeException unavailable) {
            return new FundingResult(
                    FundingStatus.UNAVAILABLE,
                    "CORE_BANKING_UNAVAILABLE", 0, null);
        }
    }

    private static CoreBankingAuthorizationRequest toRequest(
            FundingCommand command) {
        return new CoreBankingAuthorizationRequest(
                "1.0", command.issuerId(), command.fundingContractId(),
                command.operation(), command.amountMinor(), command.currency(),
                command.transactionId(), command.originalTransactionId(),
                command.correlationId(), command.idempotencyKey());
    }

    private static void validateResponse(
            FundingCommand command, CoreBankingAuthorizationResponse response) {
        if (response == null
                || !command.issuerId().equals(response.issuerId())
                || !command.transactionId().equals(response.transactionId())
                || !command.correlationId().equals(response.correlationId())
                || response.status() == null
                || response.approvedAmountMinor() < 0
                || response.approvedAmountMinor() > command.amountMinor()) {
            throw new IllegalStateException(
                    "Invalid correlated Core Banking response");
        }
    }

    private static FundingResult toResult(
            CoreBankingAuthorizationResponse response) {
        FundingStatus status = switch (response.status()) {
            case APPROVED -> FundingStatus.APPROVED;
            case PARTIALLY_APPROVED -> FundingStatus.PARTIALLY_APPROVED;
            case DECLINED -> FundingStatus.DECLINED;
            case UNAVAILABLE -> FundingStatus.UNAVAILABLE;
        };
        return new FundingResult(
                status, response.responseCode(),
                response.approvedAmountMinor(), response.fundingReference());
    }

    private static void requireRest(IssuingInterfaceEndpoint endpoint) {
        if (endpoint.protocol() != IssuingInterfaceProtocol.REST
                && endpoint.protocol() != IssuingInterfaceProtocol.REST_TLS) {
            throw new IllegalStateException(
                    "Core Banking endpoint must use REST or REST_TLS");
        }
    }

    private static String baseUrl(IssuingInterfaceEndpoint endpoint) {
        String scheme = endpoint.protocol() == IssuingInterfaceProtocol.REST_TLS
                ? "https" : "http";
        String path = endpoint.basePath() == null
                ? "" : endpoint.basePath().strip();
        if (!path.isEmpty() && !path.startsWith("/")) path = "/" + path;
        while (path.endsWith("/")) path =
                path.substring(0, path.length() - 1);
        return scheme + "://" + endpoint.host() + ":" + endpoint.port() + path;
    }
}
