package com.staging.sg.fraud.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.net.URI;
import java.util.Locale;

@ConfigurationProperties(prefix = "fraud.portal")
public record FraudPortalProperties(Mode mode, URI upstreamBaseUrl, String clientRegistrationId,
        int connectTimeoutMs, int readTimeoutMs, URI oidcEndSessionUri) {
    public FraudPortalProperties(Mode mode, URI upstreamBaseUrl, String clientRegistrationId) {
        this(mode, upstreamBaseUrl, clientRegistrationId, 3_000, 180_000,
                URI.create("https://identity.futurpayment.com/realms/futurpayment/protocol/openid-connect/logout"));
    }
    @ConstructorBinding
    public FraudPortalProperties {
        mode = mode == null ? Mode.GATEWAY : mode;
        if (upstreamBaseUrl == null || upstreamBaseUrl.getScheme() == null || upstreamBaseUrl.getHost() == null) {
            throw new IllegalArgumentException("fraud.portal.upstream-base-url must be an absolute HTTP(S) URL");
        }
        String scheme = upstreamBaseUrl.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("fraud.portal.upstream-base-url must use HTTP(S)");
        }
        if (upstreamBaseUrl.getRawQuery() != null || upstreamBaseUrl.getRawFragment() != null
                || (upstreamBaseUrl.getPath() != null && !upstreamBaseUrl.getPath().matches("/?"))) {
            throw new IllegalArgumentException("fraud.portal.upstream-base-url must not contain a path, query or fragment");
        }
        clientRegistrationId = blankToDefault(clientRegistrationId, "keycloak");
        connectTimeoutMs = bounded(connectTimeoutMs, 3_000, 500, 30_000, "connect-timeout-ms");
        readTimeoutMs = bounded(readTimeoutMs, 180_000, 1_000, 600_000, "read-timeout-ms");
        validateOidcEndSessionUri(oidcEndSessionUri);
    }

    public URI apiRoot() {
        return upstreamBaseUrl.resolve(mode == Mode.GATEWAY ? "/api/fraud-gateway/v1/" : "/api/fraud/v1/");
    }

    public String publicProductCode() {
        return mode == Mode.GATEWAY ? "FRAUD_GATEWAY" : "FRAUD_PLATFORM";
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    private static int bounded(int value, int fallback, int minimum, int maximum, String name) {
        int effective = value == 0 ? fallback : value;
        if (effective < minimum || effective > maximum) throw new IllegalArgumentException("fraud.portal." + name + " is outside the allowed range");
        return effective;
    }

    private static void validateOidcEndSessionUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("fraud.portal.oidc-end-session-uri must be an absolute HTTPS URL");
        }
    }


    public enum Mode {
        GATEWAY, PLATFORM
    }
}
