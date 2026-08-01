package com.staging.sg.acquiring.integration;

import com.staging.sg.acquiring.port.EcommerceNetworkCommand;
import com.staging.sg.acquiring.port.EcommerceNetworkException;
import com.staging.sg.acquiring.port.EcommerceNetworkPort;
import com.staging.sg.common.ecommerce.EcommerceNetworkRoute;
import com.staging.sg.common.issuing.*;
import com.staging.sg.common.issuing.client.RoutingIssuingMapper;
import com.staging.sg.common.routing.RoutingTransactionRequest;
import com.staging.sg.common.routing.RoutingTransactionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

@Component
public class HttpEcommerceNetworkAdapter implements EcommerceNetworkPort {
    private final boolean cardNetworkGatewayEnabled;
    private final boolean swamEnabled;
    private final boolean localIssuingEnabled;
    private final String localIssuerId;
    private final RestClient cardNetworkGateway;
    private final RestClient swam;
    private final RestClient localIssuing;

    public HttpEcommerceNetworkAdapter(
            @Value("${acquiring.network.card-gateway.enabled:false}") boolean cardNetworkGatewayEnabled,
            @Value("${acquiring.network.card-gateway.base-url:http://127.0.0.1:8563}") String cardNetworkGatewayUrl,
            @Value("${acquiring.network.swam.enabled:false}") boolean swamEnabled,
            @Value("${acquiring.network.swam.base-url:http://127.0.0.1:8094}") String swamUrl,
            @Value("${acquiring.issuing.enabled:false}") boolean localIssuingEnabled,
            @Value("${acquiring.issuing.base-url:http://127.0.0.1:8540/api/issuing/v1}") String localIssuingUrl,
            @Value("${acquiring.issuing.issuer-id:}") String localIssuerId,
            @Value("${acquiring.network.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${acquiring.network.read-timeout-ms:15000}") int readTimeoutMs) {
        this.cardNetworkGatewayEnabled = cardNetworkGatewayEnabled;
        this.swamEnabled = swamEnabled;
        this.localIssuingEnabled = localIssuingEnabled;
        this.localIssuerId = localIssuerId;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs)).build());
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.cardNetworkGateway = RestClient.builder().baseUrl(cardNetworkGatewayUrl)
                .requestFactory(factory).build();
        this.swam = RestClient.builder().baseUrl(swamUrl).requestFactory(factory).build();
        this.localIssuing = RestClient.builder().baseUrl(localIssuingUrl)
                .requestFactory(factory).build();
    }

    @Override
    public RoutingTransactionResponse authorize(EcommerceNetworkCommand command) {
        if (command.route() == EcommerceNetworkRoute.LOCAL_ISSUING) {
            return authorizeLocally(command);
        }
        RestClient client = switch (command.route()) {
            case DMAS_MASTERCARD, VISA -> enabled(cardNetworkGatewayEnabled,
                    cardNetworkGateway, "Visa/Mastercard gateway");
            case SWAM -> enabled(swamEnabled, swam, "SWAM");
            case AUTO -> throw new EcommerceNetworkException(
                    "AUTO route must be resolved before authorization");
            case LOCAL_ISSUING -> throw new IllegalStateException("unreachable");
        };
        RoutingTransactionRequest request = new RoutingTransactionRequest(
                "1.0", command.transactionId(), command.correlationId(),
                command.idempotencyKey(), "AUTHORIZATION", sourceMti(command.route()),
                "000000", command.pan(), command.expiry(),
                "%012d".formatted(command.amountMinor()), command.currency(),
                command.stan(), command.rrn(), command.terminalId(),
                command.merchantId(), null, null, null,
                networkAttributes(command));
        try {
            RoutingTransactionResponse response = client.post()
                    .uri("/api/routing/v1/transactions")
                    .header("Idempotency-Key", command.idempotencyKey())
                    .header("X-Correlation-ID", command.correlationId())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (ignored, result) -> {
                        throw new EcommerceNetworkException(
                                "Ecommerce network rejected the transaction: "
                                        + result.getStatusCode().value());
                    })
                    .body(RoutingTransactionResponse.class);
            if (response == null) {
                throw new EcommerceNetworkException("Empty ecommerce network response");
            }
            return response;
        } catch (EcommerceNetworkException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EcommerceNetworkException(
                    "Ecommerce network authorization failed", e);
        }
    }

    private RoutingTransactionResponse authorizeLocally(
            EcommerceNetworkCommand command) {
        enabled(localIssuingEnabled, localIssuing, "Local Issuing");
        IssuingAuthorizationRequest request = new IssuingAuthorizationRequest(
                "1.0", localIssuerId, "ECOMMERCE_ACQUIRING", command.transactionId(),
                command.correlationId(), command.idempotencyKey(),
                IssuingOperation.AUTHORIZATION, null, PaymentIdentifierType.PAN,
                command.pan(), command.amountMinor(), command.currency(), null,
                command.terminalId(), command.merchantId(), null, "504", false,
                true, null, null, null,
                localAttributes(command));
        try {
            IssuingAuthorizationResponse response = localIssuing.post()
                    .uri("/authorizations")
                    .header("Idempotency-Key", command.idempotencyKey())
                    .header("X-Correlation-ID", command.correlationId())
                    .body(request).retrieve().body(IssuingAuthorizationResponse.class);
            if (response == null) {
                throw new EcommerceNetworkException("Empty local Issuing response");
            }
            return RoutingIssuingMapper.response(response, "LOCAL_ISSUING");
        } catch (EcommerceNetworkException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EcommerceNetworkException("Local Issuing authorization failed", e);
        }
    }

    private static RestClient enabled(boolean enabled, RestClient client, String name) {
        if (!enabled) {
            throw new EcommerceNetworkException(name + " ecommerce route is disabled");
        }
        return client;
    }

    private static String sourceMti(EcommerceNetworkRoute route) {
        return route == EcommerceNetworkRoute.SWAM ? "1100" : "0100";
    }

    private static Map<String, String> networkAttributes(EcommerceNetworkCommand command) {
        Map<String, String> values = baseAttributes(command);
        values.put("entryMode", "010");
        values.put("conditionCode", "59");
        if (command.route() == EcommerceNetworkRoute.DMAS_MASTERCARD) {
            values.put("cardProgram", "MASTERCARD");
        } else if (command.route() == EcommerceNetworkRoute.VISA) {
            values.put("cardProgram", "VISA");
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> localAttributes(EcommerceNetworkCommand command) {
        return Map.copyOf(baseAttributes(command));
    }

    private static Map<String, String> baseAttributes(EcommerceNetworkCommand command) {
        Map<String, String> values = new HashMap<>();
        values.put("channel", "ECOMMERCE");
        values.put("cardPresent", "false");
        values.put("authenticationStatus", command.authenticationStatus().name());
        put(values, "eci", command.eci());
        put(values, "authenticationValue", command.authenticationValue());
        put(values, "directoryServerTransactionId",
                command.directoryServerTransactionId());
        return values;
    }

    private static void put(Map<String, String> target, String name, String value) {
        if (value != null && !value.isBlank()) target.put(name, value);
    }
}
