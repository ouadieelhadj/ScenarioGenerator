package com.staging.sg.acquiring.integration;

import com.staging.sg.acquiring.port.ServerPosProvisioningPort;
import com.staging.sg.acquiring.port.ServerPosProvisioningException;
import com.staging.sg.acquiring.port.ServerPosTerminalConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HttpServerPosProvisioningAdapter implements ServerPosProvisioningPort {
    private final boolean enabled;
    private final RestClient client;

    public HttpServerPosProvisioningAdapter(
            @Value("${acquiring.server-pos.enabled:false}") boolean enabled,
            @Value("${acquiring.server-pos.base-url:http://127.0.0.1:8530}") String baseUrl,
            @Value("${acquiring.server-pos.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${acquiring.server-pos.read-timeout-ms:3000}") int readTimeoutMs) {
        this.enabled = enabled;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public void provision(ServerPosTerminalConfiguration configuration) {
        if (!enabled) {
            throw new ServerPosProvisioningException(
                    "ServerPOS provisioning is not configured");
        }
        AtomicBoolean alreadyExists = new AtomicBoolean();
        try {
            client.post()
                    .uri("/api/admin/waypos/v1/terminals")
                    .body(Map.of(
                            "terminalId", configuration.terminalId(),
                            "merchantId", configuration.merchantId(),
                            "extendedSet", configuration.extendedSet(),
                            "macData", configuration.macData(),
                            "macRequired", configuration.macRequired(),
                            "initialBatchId", configuration.initialBatchId()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        if (response.getStatusCode().value() != 409) {
                            throw new ServerPosProvisioningException(
                                    "ServerPOS rejected terminal provisioning: "
                                            + response.getStatusCode().value());
                        }
                        alreadyExists.set(true);
                    })
                    .toBodilessEntity();
            if (alreadyExists.get() && !matchesExisting(configuration)) {
                throw new ServerPosProvisioningException(
                        "ServerPOS terminal already exists with another configuration");
            }
        } catch (ServerPosProvisioningException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServerPosProvisioningException("ServerPOS provisioning failed", e);
        }
    }

    private boolean matchesExisting(ServerPosTerminalConfiguration expected) {
        List<Map<String, Object>> terminals = client.get()
                .uri("/api/admin/waypos/v1/terminals")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (terminals == null) return false;
        return terminals.stream()
                .filter(value -> expected.terminalId().equals(value.get("terminalId")))
                .anyMatch(value -> expected.merchantId().equals(value.get("merchantId"))
                        && expected.macData().equals(value.get("macData"))
                        && expected.extendedSet() == Boolean.TRUE.equals(value.get("extendedSet"))
                        && expected.macRequired() == Boolean.TRUE.equals(value.get("macRequired")));
    }
}
