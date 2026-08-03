package com.staging.sg.threeds.network.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxChallengeUiControllerTest {
    @Test
    void exposesTheExternalAcsOtpOnlyInSandbox() {
        var response = new SandboxChallengeUiController("654321").display();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("sandbox")).isEqualTo(true);
        assertThat(body.get("otp")).isEqualTo("654321");
    }

    @Test
    void failsClosedWithoutASandboxOtp() {
        assertThat(new SandboxChallengeUiController(" ").display()
                .getStatusCode().value()).isEqualTo(503);
    }
}
