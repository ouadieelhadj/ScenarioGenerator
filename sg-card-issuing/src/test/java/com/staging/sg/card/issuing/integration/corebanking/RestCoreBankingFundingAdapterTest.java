package com.staging.sg.card.issuing.integration.corebanking;

import com.staging.sg.card.issuing.domain.IssuingInterfaceDirection;
import com.staging.sg.card.issuing.domain.IssuingInterfaceEndpoint;
import com.staging.sg.card.issuing.domain.IssuingInterfaceProtocol;
import com.staging.sg.card.issuing.domain.IssuingInterfaceType;
import com.staging.sg.card.issuing.port.FundingAuthorizationPort;
import com.staging.sg.card.issuing.service.IssuingEndpointResolver;
import com.staging.sg.common.issuing.IssuingOperation;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestCoreBankingFundingAdapterTest {
    @Test
    void mapsACorrelatedApprovalFromDatabaseEndpoint() throws Exception {
        AtomicReference<HttpExchange> received = new AtomicReference<>();
        HttpServer server = server("""
                {
                  "schemaVersion":"1.0",
                  "issuerId":"BANK1",
                  "transactionId":"tx-1",
                  "correlationId":"corr-1",
                  "status":"APPROVED",
                  "responseCode":"APPROVED",
                  "approvedAmountMinor":1250,
                  "fundingReference":"CBS-1"
                }
                """, received);
        IssuingEndpointResolver endpoints = mock(IssuingEndpointResolver.class);
        when(endpoints.requireActive("BANK1", IssuingInterfaceType.CORE_BANKING))
                .thenReturn(endpoint(server.getAddress().getPort()));
        try {
            FundingAuthorizationPort.FundingResult result =
                    new RestCoreBankingFundingAdapter(
                            endpoints, RestClient.builder())
                            .authorize(command());

            assertThat(result.status())
                    .isEqualTo(FundingAuthorizationPort.FundingStatus.APPROVED);
            assertThat(result.approvedAmountMinor()).isEqualTo(1250);
            assertThat(result.fundingReference()).isEqualTo("CBS-1");
            assertThat(received.get().getRequestURI().getPath()).isEqualTo(
                    "/api/sandbox/core-banking/v1/authorizations");
            assertThat(received.get().getRequestHeaders()
                    .getFirst("Idempotency-Key")).isEqualTo("idem-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClosedWhenResponseIsNotCorrelated() throws Exception {
        HttpServer server = server("""
                {
                  "schemaVersion":"1.0",
                  "issuerId":"OTHER",
                  "transactionId":"tx-1",
                  "correlationId":"corr-1",
                  "status":"APPROVED",
                  "responseCode":"APPROVED",
                  "approvedAmountMinor":1250
                }
                """, new AtomicReference<>());
        IssuingEndpointResolver endpoints = mock(IssuingEndpointResolver.class);
        when(endpoints.requireActive("BANK1", IssuingInterfaceType.CORE_BANKING))
                .thenReturn(endpoint(server.getAddress().getPort()));
        try {
            FundingAuthorizationPort.FundingResult result =
                    new RestCoreBankingFundingAdapter(
                            endpoints, RestClient.builder())
                            .authorize(command());

            assertThat(result.status())
                    .isEqualTo(FundingAuthorizationPort.FundingStatus.UNAVAILABLE);
            assertThat(result.responseCode())
                    .isEqualTo("CORE_BANKING_UNAVAILABLE");
        } finally {
            server.stop(0);
        }
    }

    private static IssuingInterfaceEndpoint endpoint(int port) {
        return IssuingInterfaceEndpoint.draft(
                "BANK1", IssuingInterfaceType.CORE_BANKING, 1,
                IssuingInterfaceDirection.OUTBOUND,
                IssuingInterfaceProtocol.REST, "127.0.0.1", port,
                "/api/sandbox/core-banking/v1", 500, 1000,
                null, null, "{}", "maker", "idem-endpoint", "fingerprint");
    }

    private static HttpServer server(
            String response, AtomicReference<HttpExchange> received)
            throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/sandbox/core-banking/v1/authorizations",
                exchange -> {
                    received.set(exchange);
                    byte[] body = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        return server;
    }

    private static FundingAuthorizationPort.FundingCommand command() {
        return new FundingAuthorizationPort.FundingCommand(
                "BANK1", "ACCOUNT-1", IssuingOperation.AUTHORIZATION,
                1250, "504", "tx-1", null, "corr-1", "idem-1");
    }
}
