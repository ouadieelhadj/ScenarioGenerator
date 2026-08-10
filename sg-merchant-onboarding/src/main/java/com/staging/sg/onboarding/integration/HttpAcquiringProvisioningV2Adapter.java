package com.staging.sg.onboarding.integration;

import com.staging.sg.onboarding.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Component
public class HttpAcquiringProvisioningV2Adapter implements AcquiringProvisioningV2Port {
    private final boolean enabled;
    private final RestClient acquiringClient;
    private final RestClient tokenClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private volatile Token cachedToken;

    public HttpAcquiringProvisioningV2Adapter(
            @Value("${merchant-onboarding.acquiring-v2.enabled:false}") boolean enabled,
            @Value("${merchant-onboarding.acquiring-v2.base-url:http://127.0.0.1:8550}") String baseUrl,
            @Value("${merchant-onboarding.acquiring-v2.oauth2.token-url:}") String tokenUrl,
            @Value("${merchant-onboarding.acquiring-v2.oauth2.client-id:}") String clientId,
            @Value("${merchant-onboarding.acquiring-v2.oauth2.client-secret:}") String clientSecret,
            @Value("${merchant-onboarding.acquiring-v2.oauth2.scope:merchant.provision}") String scope) {
        this.enabled = enabled;
        this.acquiringClient = RestClient.builder().baseUrl(baseUrl).build();
        this.tokenClient = RestClient.create();
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    @Override
    public MerchantProvisioningResultV2 provision(MerchantProvisioningCommandV2 command,
            String idempotencyKey, String correlationId) {
        if (!enabled)
            throw new ProvisioningTransportException(
                    "Acquiring v2 provisioning is disabled; no MID/TID was generated", false, null);
        try {
            MerchantProvisioningResultV2 result = acquiringClient.post()
                    .uri("/api/internal/acquiring/v2/merchant-provisioning")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-Correlation-ID", correlationId)
                    .body(command).retrieve().body(MerchantProvisioningResultV2.class);
            if (result == null)
                throw new ProvisioningTransportException("Acquiring v2 returned an empty result", true, null);
            return result;
        } catch (ProvisioningTransportException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            boolean retryable = exception.getStatusCode().is5xxServerError()
                    || exception.getStatusCode().value() == 429;
            throw new ProvisioningTransportException(
                    "Acquiring v2 HTTP status " + exception.getStatusCode().value(), retryable, exception);
        } catch (ResourceAccessException exception) {
            throw new ProvisioningTransportException("Acquiring v2 is temporarily unreachable", true, exception);
        }
    }

    private String accessToken() {
        Token current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30)))
            return current.value();
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30)))
                return current.value();
            if (tokenUrl.isBlank() || clientId.isBlank() || clientSecret.isBlank())
                throw new ProvisioningTransportException("OAuth2 client credentials are not configured", false, null);
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("scope", scope);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = tokenClient.post().uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            if (response == null || !(response.get("access_token") instanceof String value) || value.isBlank())
                throw new ProvisioningTransportException("OAuth2 server returned no access token", true, null);
            long expiresIn = response.get("expires_in") instanceof Number number
                    ? number.longValue() : 300L;
            cachedToken = new Token(value, Instant.now().plusSeconds(Math.max(60L, expiresIn)));
            return value;
        }
    }

    private record Token(String value, Instant expiresAt) {}
}
