package com.staging.sg.fraud.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class GatewayServiceCredentialProvider {
    private final Environment environment;
    private final String legacyToken;

    public GatewayServiceCredentialProvider(Environment environment,
            @Value("${fraud-gateway.iso.platform-service-token:}") String legacyToken) {
        this.environment = environment;
        this.legacyToken = legacyToken;
    }

    public String requireToken(String credentialReference) {
        if (credentialReference == null || credentialReference.isBlank()) {
            if (legacyToken.isBlank()) throw new IllegalStateException("Gateway service credential is not configured");
            return legacyToken;
        }
        String variable = "FRAUD_GATEWAY_SERVICE_TOKEN_"
                + credentialReference.toUpperCase().replaceAll("[^A-Z0-9]", "_");
        String token = environment.getProperty(variable, "");
        if (token.isBlank()) throw new IllegalStateException("Missing secure credential reference " + credentialReference);
        return token;
    }
}
