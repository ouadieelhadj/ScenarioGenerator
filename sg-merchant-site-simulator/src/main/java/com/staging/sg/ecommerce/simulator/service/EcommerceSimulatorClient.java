package com.staging.sg.ecommerce.simulator.service;

import com.staging.sg.common.ecommerce.*;
import com.staging.sg.common.issuing.PaymentIdentifierType;
import com.staging.sg.common.threeds.*;
import com.staging.sg.ecommerce.simulator.api.MerchantSiteType;
import com.staging.sg.ecommerce.simulator.api.InteractiveCheckoutStartResponse;
import com.staging.sg.ecommerce.simulator.api.SimulatorPurchaseRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EcommerceSimulatorClient {
    private final RestClient acquiring;
    private final RestClient threeDsMember;
    private final RestClient threeDsNetwork;
    private final String memberBaseUrl;
    private final String networkBaseUrl;
    private final String merchantSiteBaseUrl;
    private final Map<UUID, ThreeDsRReq> externalResults = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCheckout> pendingCheckouts = new ConcurrentHashMap<>();

    @Autowired
    public EcommerceSimulatorClient(
            @Value("${ecommerce-simulator.acquiring.base-url:http://127.0.0.1:8550}")
            String baseUrl,
            @Value("${ecommerce-simulator.acquiring.connect-timeout-ms:1000}")
            int connectTimeoutMs,
            @Value("${ecommerce-simulator.acquiring.read-timeout-ms:20000}")
            int readTimeoutMs,
            @Value("${ecommerce-simulator.three-ds.member-base-url:http://127.0.0.1:8560}")
            String memberUrl,
            @Value("${ecommerce-simulator.three-ds.network-base-url:http://127.0.0.1:8561}")
            String networkUrl,
            @Value("${ecommerce-simulator.public-base-url:http://127.0.0.1:8551}")
            String merchantSiteBaseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs)).build());
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.acquiring = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(factory).build();
        this.threeDsMember = RestClient.builder().baseUrl(memberUrl)
                .requestFactory(factory).build();
        this.threeDsNetwork = RestClient.builder().baseUrl(networkUrl)
                .requestFactory(factory).build();
        this.memberBaseUrl = memberUrl;
        this.networkBaseUrl = networkUrl;
        this.merchantSiteBaseUrl = merchantSiteBaseUrl;
    }

    public EcommerceSimulatorClient(String baseUrl, int connectTimeoutMs,
            int readTimeoutMs) {
        this(baseUrl, connectTimeoutMs, readTimeoutMs,
                "http://127.0.0.1:8560", "http://127.0.0.1:8561",
                "http://127.0.0.1:8551");
    }

    public EcommercePurchaseResponse purchase(SimulatorPurchaseRequest input) {
        require(input, true);
        RequestContext context = context(input);
        Authentication authentication = authenticate(input, context.transactionId(),
                context.correlationId());
        return authorize(input, context, authentication);
    }

    public InteractiveCheckoutStartResponse startInteractive(
            SimulatorPurchaseRequest input) {
        require(input, false);
        RequestContext context = context(input);
        if (input.threeDsFlow() == ThreeDsFlow.NOT_REQUESTED) {
            return InteractiveCheckoutStartResponse.completed(authorize(input, context,
                    Authentication.notPerformed()));
        }
        ThreeDsStartResponse started = input.siteType() == MerchantSiteType.INTERNATIONAL
                ? startInternational(input, context.transactionId(), context.correlationId())
                : startNational(input, context.transactionId(), context.correlationId());
        if (started.transStatus() != ThreeDsTransStatus.C) {
            return InteractiveCheckoutStartResponse.completed(
                    authorize(input, context, accepted(started)));
        }
        UUID checkoutId = UUID.randomUUID();
        pendingCheckouts.put(checkoutId, new PendingCheckout(input, context, started));
        return InteractiveCheckoutStartResponse.challenge(checkoutId,
                browserChallengeUrl(checkoutId, input, started));
    }

    public InteractiveCheckoutStartResponse startAutomatic(
            SimulatorPurchaseRequest input) {
        if (input == null || input.pan() == null) {
            throw new IllegalArgumentException("A card is required for automatic routing");
        }
        EcommerceRoutePreviewResponse preview = acquiring.post()
                .uri("/api/acquiring/v1/ecommerce/routes/resolve")
                .body(new EcommerceRoutePreviewRequest(input.pan()))
                .retrieve().body(EcommerceRoutePreviewResponse.class);
        if (preview == null || preview.networkRoute() == null
                || preview.threeDsProgram() == null || preview.issuerMode() == null) {
            throw new IllegalStateException("Acquiring returned an incomplete BIN route");
        }
        SimulatorPurchaseRequest routed = new SimulatorPurchaseRequest(
                input.transactionId(), input.correlationId(), input.idempotencyKey(),
                input.acquirerId(), input.profileId(), input.merchantOrderId(),
                input.amountMinor(), input.currency(), input.pan(), input.expiry(),
                EcommerceNetworkRoute.AUTO, input.siteType(), preview.threeDsProgram(),
                input.threeDsFlow(), preview.issuerMode(), input.challengeData());
        return startInteractive(routed);
    }

    public EcommercePurchaseResponse completeInteractive(UUID checkoutId) {
        PendingCheckout pending = pendingCheckouts.get(checkoutId);
        if (pending == null) {
            throw new IllegalArgumentException("Checkout 3DS inconnu ou deja termine");
        }
        ThreeDsStartResponse completed = pending.input().siteType()
                == MerchantSiteType.INTERNATIONAL
                ? fromExternalResult(pending.started())
                : threeDsMember.get()
                    .uri("/api/3ds/member/v1/authentications/{id}",
                            pending.started().threeDSServerTransId())
                    .retrieve().body(ThreeDsStartResponse.class);
        EcommercePurchaseResponse response = authorize(pending.input(), pending.context(),
                accepted(completed));
        pendingCheckouts.remove(checkoutId);
        return response;
    }

    private EcommercePurchaseResponse authorize(SimulatorPurchaseRequest input,
            RequestContext context, Authentication authentication) {
        EcommercePurchaseRequest request = new EcommercePurchaseRequest(
                "1.0", context.transactionId(), context.correlationId(),
                context.idempotencyKey(),
                input.acquirerId(), input.profileId(), input.merchantOrderId(),
                input.amountMinor(), input.currency(), PaymentIdentifierType.PAN,
                input.pan(), input.expiry(), input.networkRoute(),
                authentication.status(), authentication.eci(), authentication.proof(),
                authentication.dsTransId());
        EcommercePurchaseResponse response = acquiring.post()
                .uri("/api/acquiring/v1/ecommerce/transactions")
                .header("Idempotency-Key", context.idempotencyKey())
                .header("X-Correlation-ID", context.correlationId())
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

    private RequestContext context(SimulatorPurchaseRequest input) {
        String transactionId = value(input.transactionId(), UUID.randomUUID().toString());
        String correlationId = value(input.correlationId(),
                "ecom-corr-" + transactionId);
        String idempotencyKey = value(input.idempotencyKey(),
                "ecom-idem-" + transactionId);
        return new RequestContext(transactionId, correlationId, idempotencyKey);
    }

    private String browserChallengeUrl(UUID checkoutId, SimulatorPurchaseRequest input,
            ThreeDsStartResponse started) {
        String acsBase = input.issuerMode() == ThreeDsIssuerMode.MEMBER
                ? memberBaseUrl : networkBaseUrl;
        String returnUrl = UriComponentsBuilder.fromUriString(merchantSiteBaseUrl)
                .path("/")
                .queryParam("checkoutId", checkoutId)
                .queryParam("resume", "3ds")
                .build().encode().toUriString();
        return UriComponentsBuilder.fromUriString(acsBase)
                .path("/acs/challenge.html")
                .queryParam("threeDSServerTransId", started.threeDSServerTransId())
                .queryParam("dsTransId", started.dsTransId())
                .queryParam("acsTransId", started.acsTransId())
                .queryParam("messageVersion", started.messageVersion())
                .queryParam("program", started.program())
                .queryParam("returnUrl", returnUrl)
                .build().encode().toUriString();
    }

    public ThreeDsRRes receiveResult(ThreeDsRReq result) {
        if (result == null || !"RReq".equals(result.messageType())
                || !"2.3.1.1".equals(result.messageVersion())
                || result.threeDSServerTransId() == null
                || result.dsTransId() == null || result.acsTransId() == null) {
            throw new IllegalArgumentException("Invalid merchant 3DS result");
        }
        externalResults.put(result.threeDSServerTransId(), result);
        return new ThreeDsRRes("RRes", result.messageVersion(),
                result.threeDSServerTransId(), result.dsTransId(),
                result.acsTransId(), true);
    }

    private Authentication authenticate(SimulatorPurchaseRequest input,
            String transactionId, String correlationId) {
        if (input.threeDsFlow() == null
                || input.threeDsFlow() == ThreeDsFlow.NOT_REQUESTED) {
            return Authentication.notPerformed();
        }
        ThreeDsStartResponse started = input.siteType() == MerchantSiteType.INTERNATIONAL
                ? startInternational(input, transactionId, correlationId)
                : startNational(input, transactionId, correlationId);
        if (started.transStatus() == ThreeDsTransStatus.C) {
            ThreeDsCReq challenge = new ThreeDsCReq("CReq", started.messageVersion(),
                    started.threeDSServerTransId(), started.dsTransId(),
                    started.acsTransId(), input.challengeData());
            RestClient challengeClient = input.issuerMode() == ThreeDsIssuerMode.MEMBER
                    ? threeDsMember : threeDsNetwork;
            String path = input.issuerMode() == ThreeDsIssuerMode.MEMBER
                    ? "/api/3ds/member/v1/acs/creq"
                    : "/api/3ds/network/v1/external-acs/creq";
            ThreeDsCRes cres = challengeClient.post().uri(path).body(challenge)
                    .retrieve().body(ThreeDsCRes.class);
            if (cres == null || !cres.challengeCompletionInd()) {
                throw new IllegalStateException("3DS challenge did not complete");
            }
            started = input.siteType() == MerchantSiteType.INTERNATIONAL
                    ? fromExternalResult(started)
                    : threeDsMember.get()
                        .uri("/api/3ds/member/v1/authentications/{id}",
                                started.threeDSServerTransId())
                        .retrieve().body(ThreeDsStartResponse.class);
        }
        return accepted(started);
    }

    private ThreeDsStartResponse startNational(SimulatorPurchaseRequest input,
            String transactionId, String correlationId) {
        ThreeDsStartRequest request = new ThreeDsStartRequest("1.0", transactionId,
                correlationId, input.threeDsProgram(), input.threeDsFlow(),
                input.issuerMode(), input.acquirerId(), input.merchantOrderId(),
                input.amountMinor(), input.currency(), input.pan(), input.expiry());
        ThreeDsStartResponse response = threeDsMember.post()
                .uri("/api/3ds/member/v1/authentications").body(request)
                .retrieve().body(ThreeDsStartResponse.class);
        if (response == null) throw new IllegalStateException("Empty member 3DS response");
        return response;
    }

    private ThreeDsStartResponse startInternational(SimulatorPurchaseRequest input,
            String transactionId, String correlationId) {
        UUID serverId = UUID.randomUUID();
        ThreeDsAReq request = new ThreeDsAReq("AReq", "2.3.1.1", serverId,
                transactionId, correlationId, input.threeDsProgram(),
                input.threeDsFlow(), input.issuerMode(),
                ThreeDsServerMode.MERCHANT_SITE_SIMULATOR, input.acquirerId(),
                input.merchantOrderId(), input.amountMinor(), input.currency(),
                input.pan(), input.expiry());
        ThreeDsARes response = threeDsNetwork.post().uri("/api/3ds/network/v1/areq")
                .body(request).retrieve().body(ThreeDsARes.class);
        if (response == null) throw new IllegalStateException("Empty 3DS network response");
        return new ThreeDsStartResponse(response.messageVersion(),
                response.threeDSServerTransId(), response.dsTransId(),
                response.acsTransId(), response.program(), response.transStatus(),
                response.eci(), response.authenticationValue(), response.challengeUrl(),
                response.sandboxEvidence());
    }

    private ThreeDsStartResponse fromExternalResult(ThreeDsStartResponse started) {
        ThreeDsRReq result = externalResults.remove(started.threeDSServerTransId());
        if (result == null) {
            throw new IllegalStateException("Authoritative 3DS RReq was not received");
        }
        return new ThreeDsStartResponse(result.messageVersion(),
                result.threeDSServerTransId(), result.dsTransId(), result.acsTransId(),
                result.program(), result.transStatus(), result.eci(),
                result.authenticationValue(), null, result.sandboxEvidence());
    }

    private static Authentication accepted(ThreeDsStartResponse result) {
        if (result == null || result.transStatus() != ThreeDsTransStatus.Y
                || !result.sandboxEvidence() || blank(result.eci())
                || blank(result.authenticationValue()) || result.dsTransId() == null) {
            throw new IllegalStateException("Cardholder 3DS authentication failed");
        }
        return new Authentication(EcommerceAuthenticationStatus.AUTHENTICATED,
                result.eci(), result.authenticationValue(), result.dsTransId().toString());
    }

    private static void require(SimulatorPurchaseRequest input,
            boolean challengeDataRequired) {
        if (input == null || input.profileId() == null || blank(input.acquirerId())
                || blank(input.merchantOrderId()) || input.amountMinor() <= 0
                || input.currency() == null || !input.currency().matches("\\d{3}")
                || input.pan() == null || !input.pan().matches("\\d{12,19}")
                || input.expiry() == null || !input.expiry().matches("\\d{4}")
                || input.networkRoute() == null || input.siteType() == null
                || input.threeDsFlow() == null) {
            throw new IllegalArgumentException("Invalid ecommerce simulator purchase");
        }
        if (input.threeDsFlow() != ThreeDsFlow.NOT_REQUESTED
                && (input.threeDsProgram() == null || input.issuerMode() == null
                    || challengeDataRequired && input.challengeData() == null
                        && input.threeDsFlow() == ThreeDsFlow.CHALLENGE)) {
            throw new IllegalArgumentException("Incomplete 3DS simulator scenario");
        }
    }

    private static String value(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Authentication(EcommerceAuthenticationStatus status,
            String eci, String proof, String dsTransId) {
        private static Authentication notPerformed() {
            return new Authentication(EcommerceAuthenticationStatus.NOT_PERFORMED,
                    null, null, null);
        }
    }

    private record RequestContext(String transactionId, String correlationId,
            String idempotencyKey) {}

    private record PendingCheckout(SimulatorPurchaseRequest input,
            RequestContext context, ThreeDsStartResponse started) {}
}
