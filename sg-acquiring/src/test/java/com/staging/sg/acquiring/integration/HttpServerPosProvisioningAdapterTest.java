package com.staging.sg.acquiring.integration;

import com.staging.sg.acquiring.port.ServerPosTerminalConfiguration;
import com.staging.sg.acquiring.port.ServerPosProvisioningException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerPosProvisioningAdapterTest {
    @Test
    void postsTheTerminalProjectionToTheExistingServerPosApi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/admin/waypos/v1/terminals", exchange -> {
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpServerPosProvisioningAdapter adapter = new HttpServerPosProvisioningAdapter(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 10000);
            adapter.provision(new ServerPosTerminalConfiguration(UUID.randomUUID(),
                    UUID.randomUUID(), "TERM0001", "MERCHANT0000001", true,
                    "BIN", true, "000000"));

            assertEquals("POST", method.get());
            assertNotNull(body.get());
            assertTrue(body.get().contains("\"terminalId\":\"TERM0001\""));
            assertTrue(body.get().contains("\"merchantId\":\"MERCHANT0000001\""));
            assertFalse(body.get().toLowerCase().contains("key"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClosedWhenServerPosProjectionIsDisabled() {
        HttpServerPosProvisioningAdapter adapter = new HttpServerPosProvisioningAdapter(
                false, "http://127.0.0.1:1", 100, 100);
        ServerPosProvisioningException error = assertThrows(
                ServerPosProvisioningException.class,
                () -> adapter.provision(new ServerPosTerminalConfiguration(UUID.randomUUID(),
                        UUID.randomUUID(), "TERM0001", "MERCHANT0000001", false,
                        "BIN", false, "000000")));
        assertTrue(error.getMessage().contains("not configured"));
    }

    @Test
    void acceptsAnIdempotentConflictOnlyWhenExistingProfileMatches() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/admin/waypos/v1/terminals", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(409, -1);
            } else {
                byte[] response = ("[{\"terminalId\":\"TERM0001\","
                        + "\"merchantId\":\"MERCHANT0000001\","
                        + "\"extendedSet\":true,\"macData\":\"BIN\","
                        + "\"macRequired\":true}]").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        try {
            HttpServerPosProvisioningAdapter adapter = new HttpServerPosProvisioningAdapter(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 10000);
            assertDoesNotThrow(() -> adapter.provision(new ServerPosTerminalConfiguration(
                    UUID.randomUUID(), UUID.randomUUID(), "TERM0001",
                    "MERCHANT0000001", true, "BIN", true, "000000")));
        } finally {
            server.stop(0);
        }
    }
}
