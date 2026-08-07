package com.staging.sg.onboarding.integration;

import com.staging.sg.onboarding.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpAcquiringProvisioningAdapter implements AcquiringProvisioningPort {
    private final boolean enabled;
    private final RestClient client;

    public HttpAcquiringProvisioningAdapter(
            @Value("${merchant-onboarding.acquiring.enabled:false}") boolean enabled,
            @Value("${merchant-onboarding.acquiring.base-url:http://127.0.0.1:8550}") String baseUrl) {
        this.enabled = enabled;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public MerchantProvisioningResult provision(MerchantProvisioningCommand command,
            String idempotencyKey, String correlationId) {
        if (!enabled) {
            throw new IllegalStateException("Acquiring provisioning is disabled; no fictitious MID/TID was generated");
        }
        MerchantProvisioningResult result = client.post()
                .uri("/api/internal/acquiring/v1/merchant-onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-ID", correlationId)
                .body(command)
                .retrieve()
                .body(MerchantProvisioningResult.class);
        if (result == null || result.merchantId() == null
                || result.merchantAcceptorId() == null || result.merchantAcceptorId().isBlank()) {
            throw new IllegalStateException("Acquiring returned an incomplete provisioning result");
        }
        return result;
    }
}
