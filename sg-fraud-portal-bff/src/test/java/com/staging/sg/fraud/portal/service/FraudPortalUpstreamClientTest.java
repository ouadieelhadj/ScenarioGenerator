package com.staging.sg.fraud.portal.service;

import com.staging.sg.fraud.portal.config.FraudPortalProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudPortalUpstreamClientTest {
    private final FraudPortalUpstreamClient client = new FraudPortalUpstreamClient(
            new FraudPortalProperties(FraudPortalProperties.Mode.PLATFORM, URI.create("https://fraud.example"), "keycloak"));

    @Test
    void buildsOnlyUrlsUnderConfiguredApiRoot() {
        assertThat(client.target("alerts", "status=OPEN"))
                .hasToString("https://fraud.example/api/fraud/v1/alerts?status=OPEN");
        assertThatThrownBy(() -> client.target("../actuator/env", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.target("https://attacker.example", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
