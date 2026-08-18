package com.staging.sg.softpos;

import static com.staging.sg.softpos.contracts.SoftPosContracts.*;
import static org.junit.jupiter.api.Assertions.*;
import com.staging.sg.softpos.domain.SoftPosPosServerRoute;
import com.staging.sg.softpos.service.RestJsonPosServerConnector;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestJsonPosServerConnectorTest {
    @Test void sendsIdempotentInternalRequestOverHttp() throws Exception {
        AtomicReference<String> idempotency = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/softpos/v1/transactions", exchange -> {
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"status\":\"APPROVED\",\"responseCode\":\"00\",\"authorizationCode\":\"ABC123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            var route = SoftPosPosServerRoute.configured("MEMBER-A", "LAB", PosServerMode.REST_JSON,
                    "http://127.0.0.1:" + server.getAddress().getPort(), 1000, 3000, true);
            var result = new RestJsonPosServerConnector(RestClient.builder()).exchange(command("POS-TX-1"), route);
            assertEquals(TransactionStatus.APPROVED, result.status()); assertEquals("POS-TX-1", idempotency.get());
        } finally { server.stop(0); }
    }
    static PosServerPaymentCommand command(String id) { return new PosServerPaymentCommand("MEMBER-A", id, "12345678", "123456789012345", AcceptanceChannel.NFC, 1000, "MAD", "LABREF:APPROVED_CARD"); }
}
