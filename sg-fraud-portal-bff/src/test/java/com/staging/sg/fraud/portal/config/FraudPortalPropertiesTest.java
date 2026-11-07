package com.staging.sg.fraud.portal.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudPortalPropertiesTest {
    @Test
    void gatewayAndPlatformHaveDifferentStrictApiRoots() {
        FraudPortalProperties gateway = new FraudPortalProperties(FraudPortalProperties.Mode.GATEWAY,
                URI.create("https://gateway.bank.example"), "keycloak");
        FraudPortalProperties platform = new FraudPortalProperties(FraudPortalProperties.Mode.PLATFORM,
                URI.create("https://fraud.example"), "keycloak");
        assertThat(gateway.apiRoot()).hasToString("https://gateway.bank.example/api/fraud-gateway/v1/");
        assertThat(platform.apiRoot()).hasToString("https://fraud.example/api/fraud/v1/");
        assertThat(gateway.publicProductCode()).isEqualTo("FRAUD_GATEWAY");
        assertThat(platform.publicProductCode()).isEqualTo("FRAUD_PLATFORM");
    }

    @Test
    void rejectsAmbiguousOrUnsafeUpstreamUrls() {
        assertThatThrownBy(() -> new FraudPortalProperties(FraudPortalProperties.Mode.GATEWAY,
                URI.create("file:///tmp/fraud"), "keycloak")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FraudPortalProperties(FraudPortalProperties.Mode.GATEWAY,
                URI.create("https://gateway.example/api"), "keycloak")).isInstanceOf(IllegalArgumentException.class);
    }
}
